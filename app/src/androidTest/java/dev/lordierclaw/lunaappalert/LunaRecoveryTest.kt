package dev.lordierclaw.lunaappalert

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Run after LunaE2ETest, through Test-LunaRecovery.ps1, without clearing saved configuration. */
@RunWith(AndroidJUnit4::class)
class LunaRecoveryTest {
    @Test fun configurationAndMonitoringRecoverAcrossHostRestart() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val repository = (context.applicationContext as AlertApplication).repository
        val mode = InstrumentationRegistry.getArguments().getString("recovery", "reboot")
        val configuration = runBlocking { repository.snapshot() }
        assertEquals("Fixture configuration survives $mode", 2, configuration.apps.size)
        assertEquals("All rules survive $mode", 4, configuration.rules.size)
        assertTrue(runBlocking { repository.readSettings() }.monitoringEnabled)
        val fixture = "dev.lordierclaw.fixture.beta/dev.lordierclaw.fixture.MainActivity"
        if (mode == "force_stop") {
            device.executeShellCommand("am start -W -n $fixture")
            assertNull("A force-stopped app does not silently resume monitoring",
                device.wait(Until.findObject(By.text("E2E nhóm mở ứng dụng")), 4_000))
            device.executeShellCommand("am start -W -n ${context.packageName}/.MainActivity")
            SystemClock.sleep(2_000)
        } else {
            // Host verifies boot/sticky services before instrumentation restarts this process.
            device.executeShellCommand("am start -W -n ${context.packageName}/.MainActivity")
            SystemClock.sleep(2_000)
        }
        device.pressHome()
        SystemClock.sleep(1_500)
        device.executeShellCommand("am start -W -n $fixture")
        assertNotNull("Persisted configuration remains usable after $mode and reopening",
            device.wait(Until.findObject(By.text("E2E nhóm mở ứng dụng")), 20_000))
        val directory = File(context.getExternalFilesDir(null), "e2e").apply { mkdirs() }
        device.takeScreenshot(File(directory, "recovery-$mode.png"))
        device.dumpWindowHierarchy(File(directory, "recovery-$mode.xml"))
        device.findObject(By.desc("overlay_continue")).click()
    }
}
