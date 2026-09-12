package dev.lordierclaw.lunaappalert

import android.app.Notification
import android.app.NotificationManager
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import dev.lordierclaw.lunaappalert.core.*
import dev.lordierclaw.lunaappalert.runtime.SystemAccess
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Permission changes occur in the host: revocation can kill an instrumented process. */
@RunWith(AndroidJUnit4::class)
class LunaNotificationTest {
    @Test fun notificationPermissionGatesOnlyItsDeliveryAndPreservesOverrides() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val repository = (context.applicationContext as AlertApplication).repository
        val original = runBlocking { repository.snapshot() }
        assertEquals(4, original.rules.size)
        val app = original.apps.single { it.packageName == "dev.lordierclaw.fixture.alpha" }
        val denied = InstrumentationRegistry.getArguments().getString("notifications") == "denied"
        val permissions = SystemAccess(context).permissions()
        assertEquals("The host changed the real notification access", !denied, permissions.notifications)
        assertTrue(permissions.usage && permissions.overlay)
        val resolved = RuleResolver.resolve(app, original, permissions)
        assertEquals(RuleStatus.OVERRIDDEN, resolved.single { it.rule.ownerType == OwnerType.GROUP }.status)
        assertEquals(if (denied) RuleStatus.NEEDS_PERMISSION else RuleStatus.ACTIVE,
            resolved.single { it.rule.ownerType == OwnerType.APP && it.rule.triggerType == TriggerType.ON_LAUNCH }.status)
        device.executeShellCommand("am start -W -n ${context.packageName}/.MainActivity")
        device.waitForIdle(1_000)
        SystemClock.sleep(1_500)
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancelAll()
        device.pressHome()
        SystemClock.sleep(1_500)
        device.executeShellCommand("am start -W -n ${app.packageName}/dev.lordierclaw.fixture.MainActivity")
        fun launchPosted() = manager.activeNotifications.any {
            it.notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() == "E2E ưu tiên ứng dụng"
        }
        if (denied) {
            SystemClock.sleep(4_000)
            assertFalse("Denied notifications are not delivered", launchPosted())
        } else {
            val deadline = SystemClock.elapsedRealtime() + 12_000
            while (!launchPosted() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(150)
            assertTrue("Restoring permission delivers the app-owned launch alert", launchPosted())
        }
        assertNull("An app-owned rule keeps precedence even when its permission is absent",
            device.wait(Until.findObject(By.text("E2E nhóm mở ứng dụng")), 3_000))
        if (denied) {
            device.executeShellCommand("am start -W -n dev.lordierclaw.fixture.beta/dev.lordierclaw.fixture.MainActivity")
            assertNotNull("Overlay remains functional without notification permission",
                device.wait(Until.findObject(By.text("E2E nhóm mở ứng dụng")), 12_000))
        }
        val directory = File(context.getExternalFilesDir(null), "e2e").apply { mkdirs() }
        device.takeScreenshot(File(directory, if (denied) "17-notifications-denied-overlay-still-works.png" else "18-notifications-restored.png"))
        if (denied) device.findObject(By.desc("overlay_continue")).click()
        device.executeShellCommand("am start -W -n ${context.packageName}/.MainActivity")
        assertEquals("Permission changes preserve saved configuration", original, runBlocking { repository.snapshot() })
    }
}
