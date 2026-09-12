package dev.lordierclaw.lunaappalert.core

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class ForegroundReducerTest {
    private fun reducer() = ForegroundReducer("app.alert", setOf("launcher"), setOf("keyboard", "system.ui", "app.alert"))
    private fun resumed(pkg: String, at: Long) = ForegroundEvent(ForegroundEventKind.RESUMED, pkg, at)

    @Test fun normalAppSwitchProducesExactlyOneBoundary() {
        val reducer = reducer()
        assertEquals(ForegroundChange("a", 10), reducer.accept(resumed("a", 10)))
        assertEquals(ForegroundChange("b", 20), reducer.accept(resumed("b", 20)))
        assertEquals("b", reducer.currentPackageName)
        assertEquals(20L, reducer.lastBoundaryMillis)
    }

    @Test fun samePackageActivityPauseAndResumeDoNotEndSession() {
        val reducer = reducer()
        reducer.accept(resumed("a", 10))
        assertNull(reducer.accept(ForegroundEvent(ForegroundEventKind.PAUSED, "a", 20)))
        assertNull(reducer.accept(resumed("a", 21)))
        assertEquals("a", reducer.currentPackageName)
        assertEquals(10L, reducer.lastBoundaryMillis)
    }

    @Test fun homeAndOwnEditorResetEvenWhenOwnPackageIsAlsoInIgnoredSet() {
        for (pkg in listOf("launcher", "app.alert")) {
            val reducer = reducer()
            reducer.accept(resumed("a", 10))
            assertEquals(ForegroundChange(null, 20), reducer.accept(resumed(pkg, 20)))
            assertNull(reducer.currentPackageName)
            assertEquals(ForegroundChange("a", 30), reducer.accept(resumed("a", 30)))
        }
    }

    @Test fun transientSystemWindowsAndUnrelatedEventsDoNotManufactureSessions() {
        val reducer = reducer()
        reducer.accept(resumed("a", 10))
        assertNull(reducer.accept(resumed("keyboard", 20)))
        assertNull(reducer.accept(resumed("system.ui", 30)))
        assertNull(reducer.accept(ForegroundEvent(ForegroundEventKind.IGNORED, wallMillis = 40)))
        assertNull(reducer.accept(resumed("a", 50)))
        assertEquals("a", reducer.currentPackageName)
        assertEquals(10L, reducer.lastBoundaryMillis)
    }

    @Test fun screenLockShutdownResetAndRepeatedResetIsHarmless() {
        for (kind in listOf(ForegroundEventKind.SCREEN_OFF, ForegroundEventKind.LOCKED, ForegroundEventKind.SHUTDOWN)) {
            val reducer = reducer()
            reducer.accept(resumed("a", 10))
            assertEquals(ForegroundChange(null, 20), reducer.accept(ForegroundEvent(kind, wallMillis = 20)))
            assertNull(reducer.accept(ForegroundEvent(kind, wallMillis = 21)))
            assertNull(reducer.currentPackageName)
        }
    }

    @Test fun duplicateAndOutOfOrderResumesCannotRewindCurrentOwner() {
        val reducer = reducer()
        reducer.accept(resumed("a", 10))
        assertNull(reducer.accept(resumed("a", 10)))
        reducer.accept(resumed("b", 30))
        assertNull(reducer.accept(resumed("a", 20)))
        assertEquals("b", reducer.currentPackageName)
        assertEquals(30L, reducer.lastBoundaryMillis)
    }

    @Test fun rapidSwitchAwayAndBackRemainsVisibleWithinOnePollingBatch() {
        val reducer = reducer()
        reducer.accept(resumed("a", 10))
        val changes = listOf(resumed("launcher", 20), resumed("a", 21)).mapNotNull { reducer.accept(it) }
        assertEquals(listOf(ForegroundChange(null, 20), ForegroundChange("a", 21)), changes)
    }

    @Test fun preMidnightSeedPreservesForegroundAndCountsOnlyCurrentDayTail() {
        val zone = ZoneId.of("UTC")
        val midnight = Instant.parse("2026-09-13T00:00:00Z").toEpochMilli()
        val reducer = reducer()
        reducer.reset("a", midnight - 60_000)
        val daily = DailyUsageTracker { zone }
        daily.update(reducer.currentPackageName, midnight)
        assertEquals("a", reducer.currentPackageName)
        assertEquals(midnight - 60_000, reducer.lastBoundaryMillis)
        assertEquals(mapOf("a" to 120_000L), daily.totals(midnight + 120_000))
    }

    @Test fun preMidnightSeedIsSupersededByTodayHomeOrOtherAppEvent() {
        val reducer = reducer()
        reducer.reset("a", 10)
        assertEquals(ForegroundChange(null, 20), reducer.accept(resumed("launcher", 20)))
        assertEquals(ForegroundChange("b", 30), reducer.accept(resumed("b", 30)))
    }

    @Test fun historicalReplayResetDiscardsFutureCursorAndNormalizesInvalidSeeds() {
        val reducer = reducer()
        reducer.accept(resumed("a", 1_000))
        reducer.reset("a", 10)
        assertEquals(ForegroundChange("b", 20), reducer.accept(resumed("b", 20)))
        reducer.reset("launcher", 30)
        assertNull(reducer.currentPackageName)
        reducer.reset("app.alert", 40)
        assertNull(reducer.currentPackageName)
    }

    @Test fun ignoredKeyboardEventDoesNotBlockDelayedNormalForegroundEvent() {
        val reducer = reducer()
        reducer.accept(resumed("a", 10))
        assertNull(reducer.accept(resumed("keyboard", 100)))
        assertEquals(ForegroundChange("b", 50), reducer.accept(resumed("b", 50)))
    }

    @Test fun historicalDailyReplayExcludesScreenOffGapWithoutApi28ScreenEvents() {
        val dailyOwner = ForegroundReducer("app.alert", pauseEndsUsage = true)
        val daily = DailyUsageTracker { ZoneId.of("UTC") }
        val events = listOf(resumed("a", 1_000),
            ForegroundEvent(ForegroundEventKind.PAUSED, "a", 61_000),
            resumed("a", 661_000))
        events.forEach { event -> dailyOwner.accept(event)?.let { daily.update(it.packageName, it.wallMillis) } }
        assertEquals(mapOf("a" to 90_000L), daily.totals(691_000))
    }

    @Test fun dailyPauseOfPreviousActivityCannotStopAnotherForegroundPackage() {
        val dailyOwner = ForegroundReducer("app.alert", pauseEndsUsage = true)
        dailyOwner.accept(resumed("a", 10))
        dailyOwner.accept(resumed("b", 20))
        assertNull(dailyOwner.accept(ForegroundEvent(ForegroundEventKind.PAUSED, "a", 21)))
        assertEquals("b", dailyOwner.currentPackageName)
    }

    @Test fun continuousSessionTracksAmbiguousPauseForPre28CheckpointValidation() {
        val reducer = reducer()
        reducer.accept(resumed("a", 10))
        assertNull(reducer.accept(ForegroundEvent(ForegroundEventKind.PAUSED, "a", 20)))
        assertNull(reducer.accept(resumed("a", 600_000)))
        assertEquals("Live same-package tracking remains continuous", "a", reducer.currentPackageName)
        assertEquals("A checkpoint saved before this pause cannot prove continuity after process death", 20L, reducer.lastPauseMillis)
        reducer.reset("a", 10, 20)
        assertEquals("Historical midnight seed retains the last uncertain pause", 20L, reducer.lastPauseMillis)
    }
}
