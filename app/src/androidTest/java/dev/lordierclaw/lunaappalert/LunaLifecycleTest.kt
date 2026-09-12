package dev.lordierclaw.lunaappalert

import android.app.Notification
import android.app.NotificationManager
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import dev.lordierclaw.lunaappalert.runtime.InstalledApps
import dev.lordierclaw.lunaappalert.runtime.RuntimeCheckpoint
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Host removes/reinstalls fixture A between invocations; App Alert data is never cleared. */
@RunWith(AndroidJUnit4::class)
class LunaLifecycleTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val repository get() = (context.applicationContext as AlertApplication).repository
    private val alpha = "dev.lordierclaw.fixture.alpha"
    private val beta = "dev.lordierclaw.fixture.beta"
    private val baselineFile = File(context.cacheDir, "lifecycle-configuration.txt")
    private fun snapshot() = runBlocking { repository.snapshot() }
    private fun checkpoint() = RuntimeCheckpoint.decode(runBlocking { repository.readRuntime() })

    @Test fun screenLockAndInstalledPackageLifecycle() {
        val mode = InstrumentationRegistry.getArguments().getString("lifecycle", "lock")
        val original = snapshot()
        assertEquals(2, original.apps.size)
        assertEquals(4, original.rules.size)
        if (mode == "lock") baselineFile.writeText(original.toString())
        else assertEquals("All saved configuration survives package $mode", baselineFile.readText(), original.toString())
        startMain()
        when (mode) {
            "lock" -> {
                device.pressHome()
                SystemClock.sleep(1_500)
                startFixture(beta)
                awaitOverlay()
                device.findObject(By.desc("overlay_continue")).click()
                eventually("The active session is checkpointed") { checkpoint()?.session?.packageName == beta }
                val oldId = checkpoint()?.session?.sessionId
                device.sleep()
                try {
                    eventually("Screen off discards the continuous session") { checkpoint()?.session == null }
                    assertFalse(device.isScreenOn)
                } finally {
                    device.wakeUp()
                    device.executeShellCommand("input keyevent 82")
                }
                awaitOverlay()
                capture("14-lock-unlock-new-session")
                device.findObject(By.desc("overlay_continue")).click()
                eventually("Unlock starts a new session") {
                    checkpoint()?.session?.let { it.packageName == beta && it.sessionId != oldId } == true
                }
                assertNull("Continue after unlocking does not replay an old launch",
                    device.wait(Until.findObject(By.text("E2E nhóm mở ứng dụng")), 3_000))
            }
            "removed" -> {
                assertFalse(InstalledApps(context).isInstalled(alpha))
                val app = original.apps.single { it.packageName == alpha }
                requireNotNull(device.wait(Until.findObject(By.res("app_${app.id}")), 5_000)).click()
                assertNotNull("Uninstalled target has an explicit retained-configuration state",
                    device.wait(Until.findObject(By.text("Ứng dụng chưa được cài đặt")), 5_000))
                capture("15-target-uninstalled-config-retained")
            }
            "reinstalled" -> {
                assertTrue(InstalledApps(context).isInstalled(alpha))
                val app = original.apps.single { it.packageName == alpha }
                requireNotNull(device.wait(Until.findObject(By.res("app_${app.id}")), 5_000)).click()
                assertFalse(device.hasObject(By.text("Ứng dụng chưa được cài đặt")))
                val manager = context.getSystemService(NotificationManager::class.java)
                manager.cancelAll()
                device.pressHome()
                SystemClock.sleep(1_500)
                startFixture(alpha)
                eventually("Reinstalling the same package restores its actual app-owned alert") {
                    manager.activeNotifications.any {
                        it.notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() == "E2E ưu tiên ứng dụng"
                    }
                }
                assertNull("Retained app rule still overrides group rule",
                    device.wait(Until.findObject(By.text("E2E nhóm mở ứng dụng")), 3_000))
                capture("16-target-reinstalled-alert-restored")
            }
            else -> error("Unknown lifecycle mode $mode")
        }
        assertEquals("Lifecycle changes do not mutate configuration", original, snapshot())
        startMain()
    }

    private fun startMain() {
        device.executeShellCommand("am start -W -f 0x10008000 -n ${context.packageName}/.MainActivity")
        requireNotNull(device.wait(Until.findObject(By.res("home_add")), 10_000)) { "Configured home did not open" }
    }
    private fun startFixture(packageName: String) {
        device.executeShellCommand("am start -W -n $packageName/dev.lordierclaw.fixture.MainActivity")
    }
    private fun awaitOverlay() {
        assertNotNull("A fresh launch overlay appears on the external application",
            device.wait(Until.findObject(By.text("E2E nhóm mở ứng dụng")), 12_000))
    }
    private fun eventually(message: String, condition: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + 12_000
        while (!condition() && SystemClock.elapsedRealtime() < end) SystemClock.sleep(200)
        assertTrue(message, condition())
    }
    private fun capture(name: String) {
        val directory = File(context.getExternalFilesDir(null), "e2e").apply { mkdirs() }
        device.takeScreenshot(File(directory, "$name.png"))
        device.dumpWindowHierarchy(File(directory, "$name.xml"))
    }
}
