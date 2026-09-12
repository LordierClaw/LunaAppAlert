package dev.lordierclaw.lunaappalert.runtime

import android.app.KeyguardManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.view.inputmethod.InputMethodManager
import dev.lordierclaw.lunaappalert.core.*
import java.time.ZoneId

data class ForegroundTransition(val packageName: String?, val wallMillis: Long)
data class UsageSnapshot(val packageName: String?, val dailyMillis: Map<String, Long>,
    val transitions: List<ForegroundTransition>, val lastBoundaryMillis: Long, val reconstructed: Boolean,
    val lastPauseMillis: Long)

/** Maintains only current-day totals, a foreground cursor and a short deduplication window. */
class UsageObserver(private val context: Context) {
    private val manager = context.getSystemService(UsageStatsManager::class.java)
    private val power = context.getSystemService(PowerManager::class.java)
    private val keyguard = context.getSystemService(KeyguardManager::class.java)
    private val daily = DailyUsageTracker()
    private val seen = linkedMapOf<String, Long>()
    private var lastPoll = 0L
    private var date = ""
    private var zone = ""
    private var tracked = emptySet<String>()
    private val ignoredPackages: Set<String> = buildSet {
        add(context.packageName)
        add("com.android.systemui")
        context.getSystemService(InputMethodManager::class.java).inputMethodList.forEach { add(it.packageName) }
    }
    @Suppress("DEPRECATION")
    private val homePackages = context.packageManager.queryIntentActivities(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0).map { it.activityInfo.packageName }.toSet()
    private val reducer = ForegroundReducer(context.packageName,homePackages,ignoredPackages)
    private val dailyReducer = ForegroundReducer(context.packageName,homePackages,ignoredPackages,pauseEndsUsage=true)

    fun interactive(): Boolean = power.isInteractive && !keyguard.isKeyguardLocked

    fun poll(packages: Set<String>, nowMillis: Long = System.currentTimeMillis()): UsageSnapshot {
        val currentZone = ZoneId.systemDefault()
        val currentDate = DailyUsage.localDate(nowMillis, currentZone)
        val previousPoll = lastPoll
        val previousBoundary = reducer.lastBoundaryMillis
        val reconstruct = lastPoll == 0L || currentDate != date || currentZone.id != zone || packages != tracked || nowMillis < lastPoll
        val transitions = mutableListOf<ForegroundTransition>()
        val start = if (reconstruct) DailyUsage.dayStartMillis(nowMillis, currentZone) else (lastPoll - 3_000).coerceAtLeast(0)
        if (reconstruct) {
            // Seed the midnight boundary without retaining yesterday's totals or events.
            // A resumed Activity can stay foreground across midnight without another RESUME.
            val seed = manager.queryEvents((start - 86_400_000L).coerceAtLeast(0), start)
            val seedReducer = ForegroundReducer(context.packageName,homePackages,ignoredPackages)
            val dailySeedReducer = ForegroundReducer(context.packageName,homePackages,ignoredPackages,pauseEndsUsage=true)
            val prior = UsageEvents.Event()
            while (seed?.hasNextEvent() == true) {
                seed.getNextEvent(prior)
                val normalized = normalize(prior)
                seedReducer.accept(normalized)
                dailySeedReducer.accept(normalized)
            }
            daily.reset(); seen.clear()
            reducer.reset(seedReducer.currentPackageName,seedReducer.lastBoundaryMillis,seedReducer.lastPauseMillis)
            dailyReducer.reset(dailySeedReducer.currentPackageName,dailySeedReducer.lastBoundaryMillis,dailySeedReducer.lastPauseMillis)
            tracked = packages; date = currentDate; zone = currentZone.id
            daily.rebase(start, packageName = dailyReducer.currentPackageName?.takeIf { it in packages })
        }
        val events = manager.queryEvents(start, nowMillis)
        if (events == null) throw IllegalStateException("Mở khóa thiết bị để tiếp tục theo dõi.")
        val event = UsageEvents.Event()
        fun accept(normalized: ForegroundEvent) {
            reducer.accept(normalized)?.let {
                transitions += ForegroundTransition(it.packageName,it.wallMillis)
            }
            dailyReducer.accept(normalized)?.let {
                daily.update(it.packageName?.takeIf { pkg -> pkg in tracked },it.wallMillis)
            }
        }
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val key = "${event.timeStamp}:${event.eventType}:${event.packageName}:${event.className}"
            if (event.timeStamp >= nowMillis - 4_000 && seen.put(key, event.timeStamp) != null) continue
            accept(normalize(event))
        }
        if (!interactive()) accept(ForegroundEvent(ForegroundEventKind.SCREEN_OFF,wallMillis=nowMillis))
        lastPoll = nowMillis
        seen.entries.removeAll { it.value < nowMillis - 4_000 }
        return UsageSnapshot(reducer.currentPackageName, daily.totals(nowMillis).filterKeys { it in packages },
            when {
                previousPoll == 0L -> emptyList()
                nowMillis < previousPoll -> listOf(ForegroundTransition(null,nowMillis))
                reconstruct -> transitions.filter { it.wallMillis > previousBoundary }
                else -> transitions
            }, reducer.lastBoundaryMillis, reconstruct, reducer.lastPauseMillis)
    }

    fun screenInactive(nowMillis: Long = System.currentTimeMillis()) {
        reducer.accept(ForegroundEvent(ForegroundEventKind.SCREEN_OFF,wallMillis=nowMillis))
        dailyReducer.accept(ForegroundEvent(ForegroundEventKind.SCREEN_OFF,wallMillis=nowMillis))
        daily.update(null, nowMillis)
    }

    @Suppress("DEPRECATION")
    private fun normalize(event: UsageEvents.Event) = ForegroundEvent(when(event.eventType) {
        UsageEvents.Event.MOVE_TO_FOREGROUND -> ForegroundEventKind.RESUMED
        UsageEvents.Event.MOVE_TO_BACKGROUND -> ForegroundEventKind.PAUSED
        UsageEvents.Event.SCREEN_NON_INTERACTIVE -> ForegroundEventKind.SCREEN_OFF
        UsageEvents.Event.KEYGUARD_SHOWN -> ForegroundEventKind.LOCKED
        UsageEvents.Event.DEVICE_SHUTDOWN -> ForegroundEventKind.SHUTDOWN
        else -> ForegroundEventKind.IGNORED
    },event.packageName,event.timeStamp)
}
