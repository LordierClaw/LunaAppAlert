package dev.lordierclaw.lunaappalert.core

import org.junit.Assert.*
import org.junit.Test

class SessionTrackerTest {
    private var now = 0L
    private var nextId = 0
    private fun tracker() = SessionTracker("app.alert", MonotonicClock { now }) { "session-${nextId++}" }

    @Test fun samePackageActivityEventsPreserveSessionAndElapsed() {
        val tracker = tracker()
        val first = tracker.update("target")
        assertTrue(first.newSession)
        now = 60_000
        val next = tracker.update("target")
        assertFalse(next.newSession)
        assertEquals(first.sessionId, next.sessionId)
        assertEquals(60_000L, next.continuousElapsedMillis)
    }

    @Test fun homeOtherAppAndOwnEditorEndContinuousSession() {
        for (interruptingPackage in listOf(null, "other.app", "app.alert")) {
            val tracker = tracker()
            val before = tracker.update("target")
            now += 60_000
            tracker.update(interruptingPackage)
            now += 60_000
            val after = tracker.update("target")
            assertNotEquals(before.sessionId, after.sessionId)
            assertTrue(after.newSession)
            assertEquals(0L, after.continuousElapsedMillis)
        }
    }

    @Test fun lockAndScreenOffResetEvenIfForegroundPackageDoesNotChange() {
        val tracker = tracker()
        val before = tracker.update("target")
        now += 60_000
        assertNull(tracker.update("target", interactive = false).sessionId)
        val after = tracker.update("target")
        assertNotEquals(before.sessionId, after.sessionId)
        assertEquals(0L, after.continuousElapsedMillis)
    }

    @Test fun backwardsMonotonicTimeStartsFreshAndNeverProducesNegativeElapsed() {
        val tracker = tracker()
        now = 1_000_000
        val before = tracker.update("target")
        now = 10_000
        val after = tracker.update("target")
        assertTrue(after.newSession)
        assertNotEquals(before.sessionId, after.sessionId)
        assertEquals(0L, after.continuousElapsedMillis)
    }

    @Test fun resetForDisabledAppStartsFreshSessionWhenReenabled() {
        val tracker = tracker()
        val before = tracker.update("target")
        now += 60_000
        tracker.reset()
        assertNull(tracker.checkpoint())
        val after = tracker.update("target")
        assertNotEquals(before.sessionId, after.sessionId)
        assertEquals(0L, after.continuousElapsedMillis)
    }

    @Test fun validSameBootCheckpointPreservesSessionAfterProcessRecovery() {
        now = 1_000
        val before = tracker()
        val started = before.update("target")
        now += 60_000
        before.update("target")
        val checkpoint = before.checkpoint()!!
        now += 30_000
        val recovered = tracker()
        assertTrue(recovered.restore(checkpoint))
        val snapshot = recovered.update("target")
        assertFalse(snapshot.newSession)
        assertEquals(started.sessionId, snapshot.sessionId)
        assertEquals(90_000L, snapshot.continuousElapsedMillis)
    }

    @Test fun stalePostRebootOrMalformedCheckpointIsRejected() {
        now = 1_000
        val tracker = tracker()
        assertFalse(tracker.restore(SessionCheckpoint("target", "id", 5_000, 10_000)))
        assertNull(tracker.checkpoint())
        assertFalse(tracker.restore(SessionCheckpoint("target", "id", 100, 50)))
        assertFalse(tracker.restore(SessionCheckpoint("app.alert", "id", 0, 0)))
    }
}
