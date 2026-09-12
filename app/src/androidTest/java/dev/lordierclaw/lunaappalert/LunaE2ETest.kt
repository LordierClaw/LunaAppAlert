package dev.lordierclaw.lunaappalert

import android.app.Notification
import android.app.NotificationManager
import android.os.Build
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import dev.lordierclaw.lunaappalert.core.AlertType
import dev.lordierclaw.lunaappalert.core.Configuration
import dev.lordierclaw.lunaappalert.core.OwnerType
import dev.lordierclaw.lunaappalert.core.TriggerType
import dev.lordierclaw.lunaappalert.runtime.SystemAccess
import dev.lordierclaw.lunaappalert.runtime.MonitoringStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import java.io.File
import java.util.regex.Pattern

/** Cross-app tests use the actual configuration UI and ordinary installed fixture applications. */
@RunWith(AndroidJUnit4::class)
class LunaE2ETest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val repository get() = (context.applicationContext as AlertApplication).repository
    private val notifications get() = context.getSystemService(NotificationManager::class.java)
    private val alpha = "dev.lordierclaw.fixture.alpha"
    private val beta = "dev.lordierclaw.fixture.beta"
    private val screenshots = File(context.getExternalFilesDir(null), "e2e").apply { mkdirs() }

    @get:Rule val evidence = object : TestWatcher() {
        override fun failed(error: Throwable, description: Description) {
            capture("FAIL-${description.methodName}")
        }
    }

    @Test fun configurationPermissionsPrecedenceAndRealTimedAlerts() {
        assertTrue("Runner must clear application data before the main journey", snapshot().apps.isEmpty())
        startApp(context.packageName)
        tagged("welcome_setup")
        capture("00-welcome")
        tap("welcome_setup")
        capture("01-system-access-before-grants")
        tap("grant_usage")
        enableSystemAccess { SystemAccess(context).permissions().usage }
        tap("grant_overlay")
        enableSystemAccess { SystemAccess(context).permissions().overlay }
        if (Build.VERSION.SDK_INT >= 33 && !SystemAccess(context).permissions().notifications) {
            tap("grant_notifications")
            val allow = device.wait(Until.findObject(By.res("com.android.permissioncontroller", "permission_allow_button")), 5_000)
                ?: device.wait(Until.findObject(By.text(Pattern.compile("(?i)allow|cho phép"))), 5_000)
            requireNotNull(allow) { "Notification permission dialog missing" }.click()
        }
        eventually("All three system permissions granted") {
            SystemAccess(context).permissions().let { it.usage && it.overlay && it.notifications }
        }
        capture("02-system-access-ready")
        tap("access_continue")
        tap("home_add")
        tap("menu_create_group")
        enter("group_name", "Nhóm E2E")
        tap("group_save")
        eventually("Group saved") { snapshot().groups.any { it.name == "Nhóm E2E" } }
        val group = snapshot().groups.single { it.name == "Nhóm E2E" }
        openHome()
        tap("group_${group.id}")
        tap("group_add_app")
        enter("app_search", "Ứng dụng thử")
        tap("picker_app_$alpha")
        tap("picker_app_$beta")
        tap("picker_save")
        eventually("Both fixture apps stored under the group") {
            snapshot().apps.count { it.groupId == group.id && it.packageName in listOf(alpha, beta) } == 2
        }
        tap("add_group_rule")
        fillRule(TriggerType.ON_LAUNCH, AlertType.OVERLAY, "E2E nhóm mở ứng dụng")
        capture("02b-group-detail")
        val alphaApp = snapshot().apps.single { it.packageName == alpha }
        openHome()
        tap("app_${alphaApp.id}")
        tap("add_app_rule")
        fillRule(TriggerType.ON_LAUNCH, AlertType.NOTIFICATION, "E2E ưu tiên ứng dụng")
        tap("add_app_rule")
        fillRule(TriggerType.CONTINUOUS_USE, AlertType.OVERLAY, "E2E dùng liên tục", repeat = true)
        tap("add_app_rule")
        fillRule(TriggerType.DAILY_TOTAL, AlertType.NOTIFICATION, "E2E tổng ngày")
        val saved = snapshot()
        assertEquals(4, saved.rules.size)
        assertEquals(1, saved.rules.count { it.ownerType == OwnerType.GROUP })
        capture("03-app-rules-override-and-timers")

        // Group fallback applies to B, then Continue keeps B in the foreground.
        startApp(beta)
        awaitText("E2E nhóm mở ứng dụng")
        capture("04-group-launch-overlay")
        continueOverlay()
        assertTrue(device.wait(Until.hasObject(By.pkg(beta).depth(0)), 5_000))
        assertNull("Continue must not create a new launch alert", device.wait(Until.findObject(By.text("E2E nhóm mở ứng dụng")), 3_000))

        // A overrides only the conflicting launch signature; continuous/daily signatures remain active.
        startApp(alpha)
        eventually("App-owned notification overrides the group launch overlay") { hasNotification("E2E ưu tiên ứng dụng") }
        assertFalse(device.hasObject(By.text("E2E nhóm mở ứng dụng")))
        val firstLaunchPost = notificationPostTime("E2E ưu tiên ứng dụng")
        device.findObject(By.text(Pattern.compile("Mở màn hình thứ hai", Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE))).click()
        awaitText("Màn hình thứ hai")
        SystemClock.sleep(3_000)
        assertEquals("Same-package Activity switch is not a new launch", firstLaunchPost, notificationPostTime("E2E ưu tiên ứng dụng"))

        val thresholdStart = SystemClock.elapsedRealtime()
        awaitText("E2E dùng liên tục", 75_000)
        assertTrue("Continuous alert must use a real one-minute threshold", SystemClock.elapsedRealtime() - thresholdStart >= 45_000)
        eventually("Daily total notification can coexist with a continuous overlay") { hasNotification("E2E tổng ngày") }
        capture("05-real-one-minute-continuous-overlay")
        continueOverlay()
        val repeatStart = SystemClock.elapsedRealtime()
        awaitText("E2E dùng liên tục", 75_000)
        assertTrue("Repeat interval must be a real minute", SystemClock.elapsedRealtime() - repeatStart >= 50_000)
        capture("06-real-one-minute-repeat-overlay")
        exitOverlay()
        assertFalse("Exit App returns away from the external app", device.wait(Until.hasObject(By.pkg(alpha).depth(0)), 2_000))

        // A revoked conditional permission gates overlay delivery and is represented in app state.
        device.executeShellCommand("appops set ${context.packageName} SYSTEM_ALERT_WINDOW deny")
        eventually("Overlay revocation observed") { !SystemAccess(context).permissions().overlay }
        startApp(beta)
        assertNull("Revoked overlay permission blocks group overlay", device.wait(Until.findObject(By.text("E2E nhóm mở ứng dụng")), 4_000))
        startApp(context.packageName)
        capture("07-overlay-permission-revoked")
        device.executeShellCommand("appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow")
        eventually("Overlay permission restored") { SystemAccess(context).permissions().overlay }
        device.executeShellCommand("appops set ${context.packageName} GET_USAGE_STATS deny")
        eventually("Usage revocation pauses monitoring") {
            !SystemAccess(context).permissions().usage && !MonitoringStatus.state.value.running
        }
        device.pressHome()
        startApp(beta)
        assertNull("Usage denial blocks all triggers", device.wait(Until.findObject(By.text("E2E nhóm mở ứng dụng")), 3_000))
        startApp(context.packageName)
        capture("08-usage-permission-revoked")
        device.executeShellCommand("appops set ${context.packageName} GET_USAGE_STATS allow")
        device.pressHome()
        startApp(context.packageName)
        eventually("Usage monitoring resumes after access returns") { MonitoringStatus.state.value.running }
        // Runtime notification revocation may terminate this instrumented process.
        // Test-LunaNotifications.ps1 verifies actual denial and restoration across invocations.
        openHome()
        assertEquals("All UI-created rules persist across external app flows", saved, snapshot())
        capture("09-final-configured-home")
    }

    private fun fillRule(type: TriggerType, alert: AlertType, message: String, repeat: Boolean = false) {
        tap("trigger_${type.name}")
        if (type != TriggerType.ON_LAUNCH) enter("rule_threshold", "1")
        if (repeat) {
            tap("rule_repeat")
            enter("rule_repeat_interval", "1")
            enter("rule_repeat_count", "1")
        }
        tap("alert_${alert.name}")
        enter("rule_message", message)
        capture("editor-${type.name}")
        tap("rule_save")
        eventually("Rule saved: $message") { snapshot().rules.any { it.customMessage == message } }
    }

    private fun enableSystemAccess(granted: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + 15_000
        while (!granted() && SystemClock.elapsedRealtime() < end) {
            // Settings can render its app header before the permission switch.
            // Wait for the switch before treating App Alert as a list entry;
            // tapping the detail header would navigate to unrelated App info.
            val toggle = device.wait(Until.findObject(By.checkable(true)), 3_000)
                ?: device.findObject(By.res(Pattern.compile(".*:id/(switch_widget|switch|switch_bar)")))
            if (toggle != null) {
                if (toggle.isChecked) {
                    // Some Settings versions publish the changed AppOp on leaving
                    // the detail page. Do not toggle an already-enabled permission off.
                    device.pressBack()
                } else {
                    var target = toggle
                    while (!target.isClickable && target.parent != null) target = target.parent
                    target.click()
                }
            }
            else device.findObject(By.text("App Alert"))?.click()
            SystemClock.sleep(600)
        }
        assertTrue("System special access granted through Settings UI", granted())
        repeat(5) {
            if (device.wait(Until.hasObject(By.pkg(context.packageName).depth(0)), 500)) return
            device.pressBack()
            device.waitForIdle(700)
        }
        error("Did not return from Settings to the permissions screen")
    }

    private fun openHome() {
        // A save can commit before Navigation has rendered its destination. Reopen
        // the task explicitly rather than racing pending navigation with Back.
        device.executeShellCommand("am start -W -f 0x10008000 -n ${context.packageName}/.MainActivity")
        requireNotNull(device.wait(Until.findObject(By.res("home_add")), 10_000)) { "Home did not open" }
    }

    private fun startApp(packageName: String) {
        val component = if (packageName == context.packageName) "$packageName/.MainActivity" else "$packageName/dev.lordierclaw.fixture.MainActivity"
        val output = device.executeShellCommand("am start -W -n $component")
        check(!output.contains("Error:")) { output }
        device.waitForIdle(2_000)
    }

    private fun tap(tag: String) {
        tagged(tag)
        SystemClock.sleep(300)
        requireNotNull(device.findObject(By.res(tag))).click()
        device.waitForIdle(1_000)
    }
    private fun enter(tag: String, value: String) {
        val field = tagged(tag)
        field.click()
        SystemClock.sleep(450)
        field.text = value
        SystemClock.sleep(300)
        repeat(3) {
            val ime = device.executeShellCommand("dumpsys input_method")
            if (!ime.contains("mInputShown=true")) return
            device.pressBack()
            SystemClock.sleep(450)
        }
    }
    private fun tagged(tag: String): UiObject2 {
        device.wait(Until.findObject(By.res(tag)), 3_000)?.let { return it }
        for (down in listOf(true, false)) {
            repeat(7) {
                val area = device.findObject(By.scrollable(true))?.visibleBounds
                val top = area?.top ?: device.displayHeight / 5
                val bottom = area?.bottom ?: device.displayHeight * 3 / 4
                val lower = top + (bottom - top) * 4 / 5
                val upper = top + (bottom - top) / 4
                device.swipe(device.displayWidth / 2, if (down) lower else upper,
                    device.displayWidth / 2, if (down) upper else lower, 25)
                SystemClock.sleep(450)
                device.waitForIdle(1_000)
                device.wait(Until.findObject(By.res(tag)), 500)?.let { return it }
            }
        }
        error("Missing UI tag: $tag")
    }
    private fun awaitText(text: String, timeout: Long = 10_000): UiObject2 =
        requireNotNull(device.wait(Until.findObject(By.text(text)), timeout)) { "Missing text: $text after ${timeout}ms" }
    private fun continueOverlay() {
        (device.findObject(By.desc("overlay_continue")) ?: device.findObject(By.text("Tiếp tục")) ?: error("Continue action missing")).click()
        device.waitForIdle(500)
    }
    private fun exitOverlay() {
        (device.findObject(By.desc("overlay_exit")) ?: device.findObject(By.text("Thoát ứng dụng")) ?: error("Exit action missing")).click()
        device.waitForIdle(1_000)
    }
    private fun hasNotification(message: String) = notifications.activeNotifications.any {
        it.notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() == message ||
            it.notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() == message
    }
    private fun notificationPostTime(message: String): Long = notifications.activeNotifications.firstOrNull {
        it.notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() == message ||
            it.notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() == message
    }?.postTime ?: 0L
    private fun snapshot(): Configuration = runBlocking { repository.snapshot() }
    private fun eventually(message: String, timeout: Long = 10_000, condition: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + timeout
        while (!condition() && SystemClock.elapsedRealtime() < end) SystemClock.sleep(150)
        assertTrue(message, condition())
    }
    private fun capture(name: String) {
        device.takeScreenshot(File(screenshots, "$name.png"))
        device.dumpWindowHierarchy(File(screenshots, "$name.xml"))
    }
}
