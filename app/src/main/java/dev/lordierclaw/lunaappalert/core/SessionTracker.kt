package dev.lordierclaw.lunaappalert.core

import java.time.Instant
import java.time.ZoneId
import java.util.UUID

fun interface MonotonicClock { fun nowMillis(): Long }

data class SessionSnapshot(
    val packageName: String? = null,
    val sessionId: String? = null,
    val continuousElapsedMillis: Long = 0L,
    val newSession: Boolean = false,
)

data class SessionCheckpoint(
    val packageName: String,
    val sessionId: String,
    val startedAtElapsedMillis: Long,
    val lastTick: Long,
)

/**
 * Feed the currently active normal app after filtering transient system windows.
 * Home, screen-off/lock, a disabled target, and App Alert itself end the session.
 * A fresh tracker has no session. Same-boot process recovery may restore a checkpoint
 * after the caller verifies boot identity and foreground continuity from UsageEvents.
 */
class SessionTracker(
    private val selfPackageName: String,
    private val clock: MonotonicClock = MonotonicClock { System.nanoTime() / 1_000_000L },
    private val sessionIdFactory: () -> String = { UUID.randomUUID().toString() },
) {
    private var packageName: String? = null
    private var sessionId: String? = null
    private var sessionStartedAt = 0L
    private var lastTick = 0L

    fun update(
        foregroundPackage: String?,
        interactive: Boolean = true,
        nowElapsedMillis: Long = clock.nowMillis(),
    ): SessionSnapshot {
        if (!interactive || foregroundPackage.isNullOrBlank() || foregroundPackage == selfPackageName) {
            reset()
            return SessionSnapshot()
        }
        val changed = packageName != foregroundPackage || sessionId == null || nowElapsedMillis < lastTick
        if (changed) {
            packageName = foregroundPackage
            sessionId = sessionIdFactory()
            sessionStartedAt = nowElapsedMillis
        }
        lastTick = nowElapsedMillis
        return SessionSnapshot(packageName, sessionId,
            (nowElapsedMillis - sessionStartedAt).coerceAtLeast(0), changed)
    }

    fun reset() {
        packageName = null
        sessionId = null
        sessionStartedAt = 0L
        lastTick = 0L
    }

    fun checkpoint(): SessionCheckpoint? {
        val pkg = packageName ?: return null
        val id = sessionId ?: return null
        return SessionCheckpoint(pkg, id, sessionStartedAt, lastTick)
    }

    /** Boot identity and foreground continuity must be checked by the platform caller. */
    fun restore(checkpoint: SessionCheckpoint, nowElapsedMillis: Long = clock.nowMillis()): Boolean {
        if (checkpoint.packageName.isBlank() || checkpoint.packageName == selfPackageName ||
            checkpoint.sessionId.isBlank() || checkpoint.startedAtElapsedMillis < 0 ||
            checkpoint.startedAtElapsedMillis > checkpoint.lastTick || checkpoint.lastTick > nowElapsedMillis) {
            reset()
            return false
        }
        packageName = checkpoint.packageName
        sessionId = checkpoint.sessionId
        sessionStartedAt = checkpoint.startedAtElapsedMillis
        lastTick = checkpoint.lastTick
        return true
    }
}

data class UsageInterval(val packageName: String, val startMillis: Long, val endMillis: Long)

/** Calendar boundaries use the local zone, including daylight-saving transitions. */
object DailyUsage {
    fun localDate(nowMillis: Long, zone: ZoneId): String =
        Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate().toString()

    fun dayStartMillis(nowMillis: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()

    fun currentDayIntervalMillis(startMillis: Long, endMillis: Long, nowMillis: Long, zone: ZoneId): Long {
        val start = maxOf(startMillis, dayStartMillis(nowMillis, zone))
        val end = minOf(endMillis, nowMillis)
        return (end - start).coerceAtLeast(0)
    }

    /** Merges duplicate/overlapping source intervals before summing to prevent double counting. */
    fun totals(intervals: List<UsageInterval>, nowMillis: Long, zone: ZoneId): Map<String, Long> =
        intervals.groupBy { it.packageName }.mapValues { (_, values) ->
            var end = dayStartMillis(nowMillis, zone)
            var total = 0L
            values.sortedBy { it.startMillis }.forEach { interval ->
                val start = maxOf(interval.startMillis, end)
                val clippedEnd = minOf(interval.endMillis, nowMillis)
                if (clippedEnd > start) {
                    total += clippedEnd - start
                    end = clippedEnd
                }
            }
            total
        }
}
