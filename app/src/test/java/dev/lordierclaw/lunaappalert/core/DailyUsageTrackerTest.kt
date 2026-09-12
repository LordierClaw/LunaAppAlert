package dev.lordierclaw.lunaappalert.core

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class DailyUsageTrackerTest {
    private fun instant(value: String) = Instant.parse(value).toEpochMilli()
    private val utc = ZoneId.of("UTC")

    @Test fun totalsAccumulateSeparateAppsAcrossSessionsAndExcludeInactiveTime() {
        val tracker = DailyUsageTracker { utc }
        val start = instant("2026-09-12T10:00:00Z")
        tracker.update("a", start)
        tracker.update("b", start + 60_000)
        tracker.update(null, start + 180_000)
        tracker.update("a", start + 300_000)
        assertEquals(mapOf("a" to 120_000L, "b" to 120_000L), tracker.totals(start + 360_000))
    }

    @Test fun midnightRetainsOnlyCurrentDayPortionOfOngoingSession() {
        val tracker = DailyUsageTracker { utc }
        tracker.update("a", instant("2026-09-12T23:50:00Z"))
        assertEquals(600_000L, tracker.totals(instant("2026-09-13T00:10:00Z"))["a"])
        tracker.update(null, instant("2026-09-13T00:15:00Z"))
        assertEquals(900_000L, tracker.totals(instant("2026-09-13T01:00:00Z"))["a"])
    }

    @Test fun overlappingEventQueriesAndRepeatedTotalsDoNotDoubleCount() {
        val tracker = DailyUsageTracker { utc }
        val start = instant("2026-09-12T10:00:00Z")
        tracker.update("a", start)
        tracker.update("a", start)
        tracker.update("a", start + 30_000)
        tracker.update("b", start + 10_000)
        assertEquals(mapOf("a" to 60_000L), tracker.totals(start + 60_000))
        assertEquals(mapOf("a" to 60_000L), tracker.totals(start + 60_000))
    }

    @Test fun timezoneChangeClearsAmbiguousTotalsAndRebaseRestoresSourceData() {
        var zone = utc
        val tracker = DailyUsageTracker { zone }
        val start = instant("2026-09-12T17:00:00Z")
        tracker.update("a", start)
        assertEquals(60_000L, tracker.totals(start + 60_000)["a"])
        zone = ZoneId.of("Asia/Ho_Chi_Minh")
        assertTrue(tracker.totals(start + 120_000).isEmpty())
        tracker.rebase(start + 120_000, mapOf("a" to 120_000), "a")
        assertEquals(180_000L, tracker.totals(start + 180_000)["a"])
    }

    @Test fun dayBoundaryUsesLocalTimezoneAndDaylightSaving() {
        val zone = ZoneId.of("America/New_York")
        val springEnd = instant("2026-03-09T03:59:59Z")
        val springStart = DailyUsage.dayStartMillis(springEnd, zone)
        assertEquals(instant("2026-03-08T05:00:00Z"), springStart)
        assertEquals(23 * 60 * 60 * 1000L - 1_000L, springEnd - springStart)
        val autumnEnd = instant("2026-11-02T04:59:59Z")
        assertEquals(25 * 60 * 60 * 1000L - 1_000L,
            autumnEnd - DailyUsage.dayStartMillis(autumnEnd, zone))
        assertEquals("2026-09-13", DailyUsage.localDate(instant("2026-09-12T17:01:00Z"), ZoneId.of("Asia/Ho_Chi_Minh")))
    }

    @Test fun reconstructionMergesDuplicateIntervalsAndClipsHistoryAndFuture() {
        val now = instant("2026-09-12T00:10:00Z")
        val intervals = listOf(
            UsageInterval("a", now - 1_200_000, now - 300_000),
            UsageInterval("a", now - 1_200_000, now - 300_000),
            UsageInterval("a", now - 400_000, now + 300_000),
            UsageInterval("b", now - 60_000, now),
        )
        assertEquals(mapOf("a" to 600_000L, "b" to 60_000L), DailyUsage.totals(intervals, now, utc))
    }

    @Test fun resetAndRecoveryRetainOnlyExplicitlyRebasedCurrentDayUsage() {
        val now = instant("2026-09-12T10:00:00Z")
        val tracker = DailyUsageTracker { utc }
        tracker.rebase(now, mapOf("a" to 5_000), "a")
        assertEquals(65_000L, tracker.totals(now + 60_000)["a"])
        tracker.reset()
        assertTrue(tracker.totals(now + 120_000).isEmpty())
    }

    @Test fun lateForegroundSwitchBeforePriorPollCorrectsTheProjectedTail() {
        val tracker = DailyUsageTracker { utc }
        val start = instant("2026-09-12T10:00:00Z")
        tracker.update("a", start)
        assertEquals(mapOf("a" to 60_000L), tracker.totals(start + 60_000))
        tracker.update("b", start + 50_000)
        assertEquals(mapOf("a" to 50_000L, "b" to 70_000L), tracker.totals(start + 120_000))
        assertEquals(mapOf("a" to 50_000L, "b" to 70_000L), tracker.totals(start + 120_000))
    }

    @Test fun midnightProjectionDoesNotDiscardAnEventArrivingBeforeThePriorPoll() {
        val tracker = DailyUsageTracker { utc }
        val start = instant("2026-09-12T23:59:00Z")
        tracker.update("a", start)
        assertEquals(mapOf("a" to 60_000L), tracker.totals(start + 120_000))
        tracker.update("b", start + 90_000)
        assertEquals(mapOf("a" to 30_000L, "b" to 60_000L), tracker.totals(start + 150_000))
    }
}
