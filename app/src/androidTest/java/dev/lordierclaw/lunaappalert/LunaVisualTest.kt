package dev.lordierclaw.lunaappalert

import android.graphics.Rect
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import dev.lordierclaw.lunaappalert.core.TriggerType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Run after LunaE2ETest. Uses its actual configuration, and restores every temporary setting. */
@RunWith(AndroidJUnit4::class)
class LunaVisualTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val repository get() = (context.applicationContext as AlertApplication).repository
    private val evidence = File(context.getExternalFilesDir(null), "visual").apply { mkdirs() }

    @Test fun captureStitchScreensAndLargeTextLandscapeKeyboard() {
        val before = runBlocking { repository.snapshot() }
        val settings = runBlocking { repository.readSettings() }
        val group = requireNotNull(before.groups.firstOrNull()) { "Run LunaE2ETest first to create a group." }
        val continuous = requireNotNull(before.rules.firstOrNull { it.triggerType == TriggerType.CONTINUOUS_USE && before.apps.any { app -> app.id == it.ownerId } }) {
            "Run LunaE2ETest first to create an app-owned continuous rule."
        }
        val app = before.apps.single { it.id == continuous.ownerId }
        val fontScale = setting("font_scale")
        val autoRotation = setting("accelerometer_rotation")
        val userRotation = setting("user_rotation")
        val showSoftwareKeyboard = setting("show_ime_with_hard_keyboard", "secure")
        val keyboardPackage = setting("default_input_method", "secure").substringBefore('/')
        try {
            // AVDs expose a hardware keyboard. Explicitly show the software IME so
            // this test verifies actual occlusion rather than an internal IME flag.
            device.executeShellCommand("settings put secure show_ime_with_hard_keyboard 1")
            runBlocking { repository.setOnboardingCompleted(false) }
            startFresh()
            tagged("welcome_setup")
            capture("00-welcome")
            runBlocking { repository.setOnboardingCompleted(settings.onboardingCompleted) }
            startFresh()
            openHome()
            capture("01-home")
            tap("group_${group.id}")
            tagged("group_add_app")
            capture("02-group-apps-first")
            tap("group_add_app")
            tagged("app_search")
            capture("02a-installed-app-picker")
            tap("navigate_back")
            requireNotNull(device.wait(Until.findObject(By.desc("Tùy chọn nhóm")), 5_000)).click()
            requireNotNull(device.wait(Until.findObject(By.text("Chỉnh sửa nhóm")), 5_000)).click()
            tagged("group_name")
            capture("02b-edit-group")
            openHome()
            tap("home_add")
            tap("menu_create_group")
            tagged("group_name")
            capture("02c-create-group")
            tap("navigate_back")
            openHome()
            tap("app_${app.id}")
            tagged("add_app_rule")
            capture("03-app-inherited-and-own-rules")
            tap("rule_${continuous.id}")
            tagged("rule_save")
            capture("04-rule-editor")
            tap("navigate_back")
            openHome()
            tap("settings")
            tagged("monitoring_toggle")
            capture("05-settings-local-only")
            openHome()
            tap("app_${app.id}")
            tap("rule_${continuous.id}")

            device.executeShellCommand("settings put system font_scale 2.0")
            device.waitForIdle(3_000)
            tagged("rule_save")
            capture("06-rule-editor-font-200-percent")
            device.setOrientationLeft()
            device.waitForIdle(3_000)
            var field = tagged("rule_threshold")
            // A partially exposed field can exist in semantics without offering a
            // usable click target. Bring the input above the viewport edge first.
            repeat(5) {
                if (field.visibleBounds.height() < 48 * context.resources.displayMetrics.density) {
                    device.findObject(By.scrollable(true))?.scroll(Direction.DOWN, .35f)
                    device.waitForIdle(700)
                    field = tagged("rule_threshold")
                }
            }
            field.click()
            val keyboardDeadline = SystemClock.elapsedRealtime() + 5_000
            while (!keyboardShown() && SystemClock.elapsedRealtime() < keyboardDeadline) SystemClock.sleep(100)
            assertTrue("The landscape test must exercise a real visible software keyboard", keyboardShown())
            // Save the same value: exercise the real form without altering E2E configuration.
            tagged("rule_threshold").text = continuous.thresholdMinutes.toString()
            tagged("rule_threshold").click()
            val keyboard = device.wait(Until.findObject(By.pkg(keyboardPackage)), 5_000)
            assertTrue("A visible keyboard window must be in the actual UI hierarchy",
                keyboard != null && !keyboard.visibleBounds.isEmpty)
            device.waitForIdle(500)
            capture("07-rule-editor-large-landscape-keyboard")
            val inputBounds = tagged("rule_threshold").visibleBounds
            val unobscuredHeight = minOf(inputBounds.bottom, requireNotNull(keyboard).visibleBounds.top) - inputBounds.top
            assertTrue("The focused input must retain at least 48dp of visible height above the keyboard",
                unobscuredHeight >= 48 * context.resources.displayMetrics.density)
            assertReachable("rule_save")
            assertReachable("navigate_back")
            tap("rule_save")
            tagged("add_app_rule")
            assertEquals("Saving under large text/landscape preserves the existing rule", before, runBlocking { repository.snapshot() })
            assertReachable("navigate_back")
            tap("navigate_back")
            tagged("home_add")
            capture("08-home-large-landscape")
        } catch (failure: Throwable) {
            capture("FAIL-visual-and-accessibility")
            throw failure
        } finally {
            runBlocking { repository.setOnboardingCompleted(settings.onboardingCompleted) }
            restoreSetting("font_scale", fontScale)
            restoreSetting("accelerometer_rotation", autoRotation)
            restoreSetting("user_rotation", userRotation)
            restoreSetting("show_ime_with_hard_keyboard", showSoftwareKeyboard, "secure")
            device.unfreezeRotation()
            device.waitForIdle(2_000)
            startFresh()
        }
        assertEquals("Visual inspection must not alter app/group/rule configuration", before, runBlocking { repository.snapshot() })
        assertEquals("Onboarding and monitoring settings are restored", settings, runBlocking { repository.readSettings() })
    }

    private fun setting(key: String, namespace: String = "system") = device.executeShellCommand("settings get $namespace $key").trim()
    private fun keyboardShown() = device.executeShellCommand("dumpsys input_method").contains("mInputShown=true")
    private fun restoreSetting(key: String, value: String, namespace: String = "system") {
        if (value.isBlank() || value == "null") device.executeShellCommand("settings delete $namespace $key")
        else device.executeShellCommand("settings put $namespace $key $value")
    }
    private fun startFresh() {
        val output = device.executeShellCommand("am start -W -f 0x10008000 -n ${context.packageName}/.MainActivity")
        check(!output.contains("Error:")) { output }
        device.waitForIdle(2_000)
    }
    private fun openHome() {
        startFresh()
        requireNotNull(device.wait(Until.findObject(By.res("home_add")), 10_000)) { "Configured home unavailable" }
    }
    private fun tap(tag: String) { tagged(tag).click(); device.waitForIdle(700) }
    private fun tagged(tag: String): UiObject2 {
        device.wait(Until.findObject(By.res(tag)), 3_000)?.let { return it }
        for (direction in listOf(Direction.DOWN, Direction.UP)) {
            repeat(8) {
                val scroll = device.findObject(By.scrollable(true))
                if (scroll != null) runCatching { scroll.scroll(direction, .65f) }
                else if (direction == Direction.DOWN) device.swipe(device.displayWidth / 2, device.displayHeight * 13 / 20, device.displayWidth / 2, device.displayHeight / 3, 25)
                else device.swipe(device.displayWidth / 2, device.displayHeight / 3, device.displayWidth / 2, device.displayHeight * 13 / 20, 25)
                SystemClock.sleep(450)
                device.waitForIdle(500)
                device.wait(Until.findObject(By.res(tag)), 400)?.let { return it }
            }
        }
        error("Missing UI control: $tag")
    }
    private fun assertReachable(tag: String) {
        val control = tagged(tag)
        val bounds = control.visibleBounds
        assertTrue("$tag has visible touch bounds", !bounds.isEmpty && Rect.intersects(bounds, Rect(0, 0, device.displayWidth, device.displayHeight)))
        assertTrue("$tag is enabled", control.isEnabled)
        assertTrue("$tag is clickable", control.isClickable)
    }
    private fun capture(name: String) {
        device.takeScreenshot(File(evidence, "$name.png"))
        device.dumpWindowHierarchy(File(evidence, "$name.xml"))
    }
}
