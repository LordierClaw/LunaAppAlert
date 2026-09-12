package dev.lordierclaw.lunaappalert.runtime

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.core.app.NotificationCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import dev.lordierclaw.lunaappalert.MainActivity
import dev.lordierclaw.lunaappalert.R
import dev.lordierclaw.lunaappalert.core.*

data class QueuedAlert(val alert: DueAlert, val sessionId: String)

/** Delivers only while a rule and the originating foreground session remain active. */
class AlertDispatcher(private val context: Context, private val valid: (QueuedAlert) -> Boolean,
    private val appName: (String) -> String, private val onExit: () -> Unit) {
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val windows = context.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val queue = mutableListOf<QueuedAlert>()
    private var shown: QueuedAlert? = null
    private var overlay: View? = null
    private var nextAllowed = 0L
    private val showNext = Runnable { showNext() }

    init { createChannels(context) }

    fun dispatch(alerts: List<DueAlert>, sessionId: String) {
        alerts.forEach { alert ->
            val queued = QueuedAlert(alert, sessionId)
            if (!valid(queued)) return@forEach
            if (alert.rule.alertType == AlertType.NOTIFICATION) postNotification(alert)
            else {
                queue.removeAll { it.alert.rule.id == alert.rule.id && it.alert.appId == alert.appId && it.sessionId == sessionId }
                queue += queued
            }
        }
        queue.sortWith(compareBy<QueuedAlert> { it.alert.dueAtMillis }
            .thenBy { if (it.alert.rule.ownerType == OwnerType.APP) 0 else 1 }.thenBy { it.alert.rule.id })
        refresh()
    }

    fun refresh() {
        queue.removeAll { !valid(it) }
        if (shown?.let { !valid(it) } == true) dismiss()
        showNext()
    }

    fun clear() {
        queue.clear()
        handler.removeCallbacks(showNext)
        dismiss(schedule = false)
    }

    private fun showNext() {
        if (overlay != null || queue.isEmpty()) return
        val wait = nextAllowed - android.os.SystemClock.elapsedRealtime()
        if (wait > 0) {
            handler.removeCallbacks(showNext)
            handler.postDelayed(showNext, wait)
            return
        }
        val item = queue.removeAt(0)
        if (!valid(item)) { showNext(); return }
        try {
            val view = createOverlay(item)
            val params = WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                0, android.graphics.PixelFormat.OPAQUE)
            params.gravity = Gravity.TOP or Gravity.START
            windows.addView(view, params)
            shown = item
            overlay = view
        } catch (_: SecurityException) {
            MonitoringStatus.update(true, "Cần cấp quyền hiển thị trên ứng dụng khác")
        } catch (_: WindowManager.BadTokenException) {
            MonitoringStatus.update(true, "Không thể hiển thị cảnh báo trên màn hình này")
        }
    }

    private fun dismiss(schedule: Boolean = true) {
        overlay?.let { runCatching { windows.removeView(it) } }
        overlay = null
        shown = null
        nextAllowed = android.os.SystemClock.elapsedRealtime() + 1_000
        if (schedule) { handler.removeCallbacks(showNext); handler.postDelayed(showNext, 1_000) }
    }

    private fun createOverlay(item: QueuedAlert): View {
        val alert = item.alert
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.WHITE)
            setPadding(dp(20), dp(36), dp(20), dp(24))
            contentDescription = "overlay_alert"
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(dp(20) + bars.left, dp(24) + bars.top, dp(20) + bars.right, dp(24) + bars.bottom)
            insets
        }
        val scroll = ScrollView(context).apply { isFillViewport = true; clipToPadding = false }
        val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(12), dp(24), dp(12), dp(24)) }
        val icon = ImageView(context).apply {
            setImageDrawable(InstalledApps(context).icon(alert.packageName)); scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = appName(alert.appId)
        }
        content.addView(icon, LinearLayout.LayoutParams(dp(64), dp(64)).apply { bottomMargin = dp(20) })
        content.addView(label(appName(alert.appId), 18f, Color.rgb(26,26,26), true))
        content.addView(label(contextText(alert.rule), 13f, Color.rgb(102,112,133)).apply { setPadding(0,dp(8),0,0) })
        content.addView(label(alert.rule.displayMessage(appName(alert.appId)), 28f, Color.rgb(26,26,26), true).apply { setPadding(0,dp(36),0,dp(24)) })
        scroll.addView(content, FrameLayout.LayoutParams(-1,-1))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(actionButton("Thoát ứng dụng", true, "overlay_exit") {
            onExit()
            // A direct user click returns to the launcher; it never force-stops another app.
            runCatching { context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            queue.clear()
            dismiss()
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        root.addView(actionButton("Tiếp tục", false, "overlay_continue") { dismiss() }, LinearLayout.LayoutParams(-1, -2))
        if (alert.rule.triggerType == TriggerType.CONTINUOUS_USE && alert.rule.repeatEnabled && alert.occurrence < (alert.rule.repeatMaxCount ?: 0)) {
            root.addView(label("Nhắc lại sau ${alert.rule.repeatEveryMinutes} phút nếu bạn tiếp tục sử dụng.", 13f, Color.rgb(102,112,133))
                .apply { setPadding(dp(8),dp(16),dp(8),0) })
        }
        return root
    }

    private fun label(text: String, size: Float, color: Int, bold: Boolean = false) = TextView(context).apply {
        this.text = text; textSize = size; setTextColor(color); gravity = Gravity.CENTER
        typeface = ResourcesCompat.getFont(context, if (bold) R.font.inter_semibold else R.font.inter_regular)
        setLineSpacing(dp(3).toFloat(), 1f)
    }
    private fun actionButton(text: String, primary: Boolean, description: String, click: () -> Unit) = Button(context).apply {
        this.text = text; textSize = 16f; isAllCaps = false; contentDescription = description
        minimumHeight = dp(56)
        setPadding(dp(16),dp(12),dp(16),dp(12))
        typeface = ResourcesCompat.getFont(context,R.font.inter_semibold)
        setTextColor(if(primary) Color.WHITE else Color.rgb(26,26,26))
        background = GradientDrawable().apply {
            cornerRadius = dp(12).toFloat(); setColor(if(primary) Color.rgb(37,99,235) else Color.WHITE)
            if(!primary) setStroke(dp(1),Color.rgb(229,231,235))
        }
        setOnClickListener { click() }
    }
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()

    @Suppress("MissingPermission")
    private fun postNotification(alert: DueAlert) {
        if (!SystemAccess(context).permissions().notifications) return
        val intent = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, ALERT_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("${appName(alert.appId)} · ${contextText(alert.rule)}")
            .setContentText(alert.rule.displayMessage(appName(alert.appId)))
            .setStyle(NotificationCompat.BigTextStyle().bigText(alert.rule.displayMessage(appName(alert.appId))))
            .setContentIntent(intent).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER).build()
        try { notifications.notify("${alert.appId}:${alert.rule.id}", 1, notification) } catch (_: SecurityException) { /* Permission may be revoked between check and post. */ }
    }

    companion object {
        const val ALERT_CHANNEL = "usage_alerts"
        const val MONITOR_CHANNEL = "monitoring"
        fun createChannels(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(ALERT_CHANNEL, "Cảnh báo sử dụng ứng dụng", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Lời nhắc theo quy tắc bạn đã chọn"
            })
            manager.createNotificationChannel(NotificationChannel(MONITOR_CHANNEL, "Trạng thái theo dõi", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Hiển thị khi App Alert đang theo dõi ứng dụng"; setSound(null,null)
            })
        }
        fun contextText(rule: Rule): String = when(rule.triggerType) {
            TriggerType.ON_LAUNCH -> "Khi mở ứng dụng"
            TriggerType.CONTINUOUS_USE -> "Dùng liên tục · ${rule.thresholdMinutes} phút"
            TriggerType.DAILY_TOTAL -> "Hôm nay · ${rule.thresholdMinutes} phút"
        }
    }
}
