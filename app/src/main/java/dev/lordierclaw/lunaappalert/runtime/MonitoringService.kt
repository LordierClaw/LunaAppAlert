package dev.lordierclaw.lunaappalert.runtime

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.os.*
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dev.lordierclaw.lunaappalert.AlertApplication
import dev.lordierclaw.lunaappalert.MainActivity
import dev.lordierclaw.lunaappalert.core.*
import kotlinx.coroutines.*
import java.time.ZoneId

class MonitoringService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val repository get() = (application as AlertApplication).repository
    private var polling: Job? = null
    private var observer: UsageObserver? = null
    private val sessionTracker = SessionTracker("dev.lordierclaw.lunaappalert", MonotonicClock { SystemClock.elapsedRealtime() })
    private var session = SessionSnapshot()
    private var configuration = Configuration()
    private var evaluation = EvaluationState()
    private var checkpoint: RuntimeCheckpoint? = null
    private var lastSaved = 0L
    private var initial = true
    private var sessionInvalidatedAt: Long? = null
    private var bootCount = 0
    private lateinit var dispatcher: AlertDispatcher
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) {
                sessionTracker.reset(); session = SessionSnapshot()
                sessionInvalidatedAt = System.currentTimeMillis()
                dispatcher.clear()
                scope.launch { saveCheckpoint() }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        bootCount = Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT, 0)
        AlertDispatcher.createChannels(this)
        dispatcher = AlertDispatcher(this, ::isValid, { id -> configuration.apps.firstOrNull { it.id == id }?.displayName ?: "Ứng dụng" }) {
            sessionTracker.reset(); session = SessionSnapshot(); sessionInvalidatedAt = System.currentTimeMillis()
        }
        ContextCompat.registerReceiver(this, screenReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
        val launch = PendingIntent.getActivity(this,0,Intent(this, MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this,1,Intent(this,MonitoringService::class.java).setAction(ACTION_STOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this,AlertDispatcher.MONITOR_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("App Alert đang theo dõi")
            .setContentText("Nhắc bạn theo quy tắc đã chọn. Chạm để quản lý.")
            .setContentIntent(launch).setOngoing(true).setSilent(true)
            .addAction(android.R.drawable.ic_media_pause,"Tạm dừng",stop).build()
        ServiceCompat.startForeground(this,MONITOR_ID,notification,
            if(Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if(intent?.action == ACTION_STOP) {
            scope.launch {
                repository.setMonitoringEnabled(false)
                sessionTracker.reset(); session = SessionSnapshot(); dispatcher.clear()
                saveCheckpoint(); stopSelf()
            }
            return START_NOT_STICKY
        }
        if(polling?.isActive != true) polling = scope.launch {
            if(!repository.readSettings().monitoringEnabled) { stopSelf(); return@launch }
            checkpoint = RuntimeCheckpoint.decode(repository.readRuntime())
            evaluation = checkpoint?.evaluation ?: EvaluationState()
            while(isActive) {
                try {
                    if(!repository.readSettings().monitoringEnabled) { stopSelf(); break }
                    tick()
                } catch (_: SecurityException) {
                    resetActive()
                    MonitoringStatus.update(false,"Quyền hệ thống đã thay đổi. Mở App Alert để kiểm tra.")
                } catch (e: Exception) {
                    if(e is CancellationException) throw e
                    resetActive()
                    MonitoringStatus.update(false,"Theo dõi cần chú ý. Mở App Alert để kiểm tra.")
                    android.util.Log.e("AppAlertMonitor","Observer could not continue",e)
                }
                delay(1_000)
            }
        }
        return START_STICKY
    }

    private suspend fun tick() {
        val permissions = SystemAccess(this).permissions()
        if(!permissions.usage) {
            resetActive(); observer = null
            MonitoringStatus.update(false,"Cần cấp quyền truy cập sử dụng ứng dụng")
            return
        }
        val updated = repository.snapshot()
        if(updated != configuration) {
            configuration = updated
            evaluation = RuleEvaluator.prune(evaluation, configuration)
        }
        if(observer == null) observer = UsageObserver(this)
        val reader = requireNotNull(observer)
        sessionInvalidatedAt?.let { reader.screenInactive(it); sessionInvalidatedAt = null }
        if(!reader.interactive()) {
            resetActive(); reader.screenInactive()
            MonitoringStatus.update(true,"Đang theo dõi · chờ màn hình hoạt động")
            return
        }
        val nowWall = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        val snapshot = withContext(Dispatchers.IO) { reader.poll(configuration.apps.map { it.packageName }.toSet(),nowWall) }
        if(sessionInvalidatedAt != null || !reader.interactive()) {
            resetActive()
            reader.screenInactive(sessionInvalidatedAt ?: System.currentTimeMillis())
            sessionInvalidatedAt = null
            return
        }
        fun enabledPackage(pkg: String?): String? {
            val app = configuration.apps.firstOrNull { it.packageName == pkg } ?: return null
            return pkg.takeIf { app.enabled && (app.groupId == null || configuration.groups.any { group -> group.id == app.groupId && group.enabled }) && InstalledApps(this).isInstalled(app.packageName) }
        }
        if(initial) {
            val restored = checkpoint
            // Before API 28 a historical pause can hide a lock/unlock interval. Do
            // not restore elapsed continuous time across an unproven background gap.
            val noAmbiguousBackgroundGap = restored != null &&
                (Build.VERSION.SDK_INT >= 28 || snapshot.lastPauseMillis <= restored.wallMillis)
            if(restored != null && restored.bootCount == bootCount && restored.session?.packageName == enabledPackage(snapshot.packageName) && snapshot.lastBoundaryMillis <= restored.wallMillis && noAmbiguousBackgroundGap) {
                restored.session?.let { sessionTracker.restore(it,nowElapsed) }
            }
        }
        val before = session.sessionId
        for(transition in snapshot.transitions) {
            val timestamp = (nowElapsed - (nowWall - transition.wallMillis).coerceAtLeast(0)).coerceAtLeast(0)
            sessionTracker.update(enabledPackage(transition.packageName),true,timestamp)
        }
        session = sessionTracker.update(enabledPackage(snapshot.packageName),true,nowElapsed)
        val newSession = session.sessionId != null && session.sessionId != before && !initial
        val app = configuration.apps.firstOrNull { it.packageName == session.packageName }
        if(app != null) {
            val resolved = RuleResolver.resolve(app,configuration,permissions,installed=true,monitoringEnabled=true)
            val result = RuleEvaluator.evaluate(app,resolved,EvaluationContext(session.sessionId,session.continuousElapsedMillis,
                snapshot.dailyMillis[app.packageName] ?: 0L,DailyUsage.localDate(nowWall,ZoneId.systemDefault()),newSession,nowWall),evaluation)
            evaluation = result.state
            if(result.dueAlerts.isNotEmpty()) {
                saveCheckpoint()
                dispatcher.dispatch(result.dueAlerts,requireNotNull(session.sessionId))
            }
        } else {
            evaluation = evaluation.copy(sessionId=null,sessionOccurrences=emptyMap())
        }
        initial = false
        dispatcher.refresh()
        if(nowElapsed - lastSaved >= 5_000) saveCheckpoint()
        val activeRules = configuration.apps.sumOf { target ->
            RuleResolver.resolve(target,configuration,permissions,InstalledApps(this).isInstalled(target.packageName),true).count { it.status == RuleStatus.ACTIVE }
        }
        val needsPermission = configuration.apps.any { target -> RuleResolver.resolve(target,configuration,permissions,
            InstalledApps(this).isInstalled(target.packageName),true).any { it.status == RuleStatus.NEEDS_PERMISSION } }
        MonitoringStatus.update(true, when {
            needsPermission -> "Đang theo dõi · một số quy tắc cần cấp quyền"
            activeRules == 0 -> "Đang theo dõi · chưa có quy tắc hoạt động"
            else -> "Đang theo dõi · $activeRules quy tắc hoạt động"
        })
    }

    private fun isValid(item: QueuedAlert): Boolean {
        if(session.sessionId != item.sessionId || session.packageName != item.alert.packageName || observer?.interactive() != true) return false
        val app = configuration.apps.firstOrNull { it.id == item.alert.appId } ?: return false
        return RuleResolver.resolve(app,configuration,SystemAccess(this).permissions(),InstalledApps(this).isInstalled(app.packageName),true)
            .any { it.rule == item.alert.rule && it.status == RuleStatus.ACTIVE }
    }
    private fun resetActive() { sessionTracker.reset(); session = SessionSnapshot(); dispatcher.clear() }
    private suspend fun saveCheckpoint() {
        repository.saveRuntime(RuntimeCheckpoint(bootCount,System.currentTimeMillis(),evaluation,sessionTracker.checkpoint()).encode())
        lastSaved = SystemClock.elapsedRealtime()
    }
    override fun onDestroy() {
        dispatcher.clear()
        unregisterReceiver(screenReceiver)
        scope.cancel()
        if(MonitoringStatus.state.value.running) MonitoringStatus.update(false,"Đã dừng theo dõi")
        super.onDestroy()
    }
    override fun onBind(intent: Intent?) = null

    companion object {
        const val ACTION_STOP = "dev.lordierclaw.lunaappalert.STOP_MONITORING"
        private const val MONITOR_ID = 100
        fun start(context: Context) {
            try { ContextCompat.startForegroundService(context,Intent(context,MonitoringService::class.java)) }
            catch (_: RuntimeException) { MonitoringStatus.update(false,"Mở App Alert để tiếp tục theo dõi") }
        }
        fun stop(context: Context) {
            context.stopService(Intent(context,MonitoringService::class.java))
            MonitoringStatus.update(false,"Đã tạm dừng theo dõi")
        }
    }
}
