package dev.lordierclaw.lunaappalert.core

import java.time.ZoneId

/**
 * Reduces chronologically ordered foreground events into current-day totals.
 * The caller passes null for Home, lock, screen-off, and excluded packages.
 * Rebase from Android's current-day source after process recovery or a zone change.
 */
class DailyUsageTracker(
    private val zoneProvider: () -> ZoneId = { ZoneId.systemDefault() },
) {
    private var zoneId: String? = null
    private var date: String? = null
    private var lastWallMillis: Long? = null
    private var foregroundPackage: String? = null
    private val accumulated = mutableMapOf<String, Long>()

    fun update(packageName: String?, wallMillis: Long) {
        val previous = lastWallMillis
        // Replaying an overlapping UsageEvents query must not manufacture usage.
        if (previous != null && wallMillis < previous) return
        advance(wallMillis)
        foregroundPackage = packageName?.takeIf { it.isNotBlank() }
    }

    /**
     * Projects usage at the poll time without advancing the committed event cursor.
     * UsageEvents can deliver a foreground switch just before the preceding poll;
     * committing a speculative tail here would make that legitimate event look stale.
     */
    fun totals(wallMillis: Long): Map<String, Long> {
        val zone = zoneProvider()
        val currentDate = DailyUsage.localDate(wallMillis, zone)
        val sameZone = zoneId == null || zoneId == zone.id
        val result = if (date == currentDate && sameZone) accumulated.toMutableMap() else mutableMapOf()
        val previous = lastWallMillis
        if (previous != null && sameZone && wallMillis > previous) {
            foregroundPackage?.let { pkg ->
                val delta = DailyUsage.currentDayIntervalMillis(previous, wallMillis, wallMillis, zone)
                if (delta > 0) result[pkg] = (result[pkg] ?: 0L) + delta
            }
        }
        return result
    }

    fun reset() {
        zoneId = null
        date = null
        lastWallMillis = null
        foregroundPackage = null
        accumulated.clear()
    }

    fun rebase(
        wallMillis: Long,
        totals: Map<String, Long> = emptyMap(),
        packageName: String? = null,
    ) {
        val zone = zoneProvider()
        zoneId = zone.id
        date = DailyUsage.localDate(wallMillis, zone)
        lastWallMillis = wallMillis
        foregroundPackage = packageName
        accumulated.clear()
        accumulated.putAll(totals.filterValues { it >= 0 })
    }

    private fun advance(wallMillis: Long) {
        val zone = zoneProvider()
        val currentDate = DailyUsage.localDate(wallMillis, zone)
        val zoneChanged = zoneId != null && zoneId != zone.id
        if (date != currentDate || zoneChanged) accumulated.clear()
        val previous = lastWallMillis
        // Old totals cannot be translated across zones; caller can rebase from the source.
        if (previous != null && !zoneChanged) {
            foregroundPackage?.let { pkg ->
                val delta = DailyUsage.currentDayIntervalMillis(previous, wallMillis, wallMillis, zone)
                if (delta > 0) accumulated[pkg] = (accumulated[pkg] ?: 0L) + delta
            }
        }
        zoneId = zone.id
        date = currentDate
        lastWallMillis = wallMillis
    }
}
