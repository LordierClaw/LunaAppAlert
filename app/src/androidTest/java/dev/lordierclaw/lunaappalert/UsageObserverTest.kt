package dev.lordierclaw.lunaappalert

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import dev.lordierclaw.lunaappalert.runtime.SystemAccess
import dev.lordierclaw.lunaappalert.runtime.UsageObserver
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Reconstructs from actual Android events after a real locked interval. */
@RunWith(AndroidJUnit4::class)
class UsageObserverTest {
    @Test fun reconstructionExcludesTimeSpentWithScreenLocked() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val target = "dev.lordierclaw.fixture.alpha"
        assertTrue("Run the main permission journey first", SystemAccess(context).permissions().usage)
        device.executeShellCommand("am start -W -n $target/dev.lordierclaw.fixture.MainActivity")
        SystemClock.sleep(3_000)
        val before = UsageObserver(context).poll(setOf(target)).dailyMillis[target] ?: 0L
        val lockStart = SystemClock.elapsedRealtime()
        try {
            device.sleep()
            assertFalse(device.isScreenOn)
            SystemClock.sleep(20_000)
            device.wakeUp()
            device.pressMenu()
            device.executeShellCommand("am start -W -n $target/dev.lordierclaw.fixture.MainActivity")
            SystemClock.sleep(2_000)
            val after = UsageObserver(context).poll(setOf(target)).dailyMillis[target] ?: 0L
            val elapsed = SystemClock.elapsedRealtime() - lockStart
            val delta = after - before
            File(context.getExternalFilesDir(null), "e2e").apply { mkdirs() }
                .resolve("daily-reconstruction-lock.txt")
                .writeText("before=$before\nafter=$after\ndelta=$delta\nwallElapsed=$elapsed\nlockedAtLeast=20000\n")
            assertTrue("Historical usage must not include the 20-second locked interval: delta=$delta, elapsed=$elapsed",
                delta in 0 until elapsed - 15_000)
        } finally {
            device.wakeUp()
            device.pressMenu()
            device.executeShellCommand("am start -W -n ${context.packageName}/.MainActivity")
        }
    }
}
