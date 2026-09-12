package dev.lordierclaw.lunaappalert

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import dev.lordierclaw.lunaappalert.core.OwnerType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Runs after the main E2E journey; all verified mutations and restorations use the UI. */
@RunWith(AndroidJUnit4::class)
class LunaRuleCrudTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val repository get() = (context.applicationContext as AlertApplication).repository
    private fun snapshot() = runBlocking { repository.snapshot() }

    @Test fun renameGroupEditMessageAndUndoRuleDeletion() {
        val baseline = snapshot()
        assertEquals("Run the main journey first: two configured apps", 2, baseline.apps.size)
        assertEquals("Run the main journey first: four configured rules", 4, baseline.rules.size)
        val group = baseline.groups.single { it.name == "Nhóm E2E" }
        // The final app rule restores to the same display position after delete + Undo.
        val rule = baseline.rules.last().also { assertEquals(OwnerType.APP, it.ownerType) }
        val app = baseline.apps.single { it.id == rule.ownerId }
        val renamed = "Nhóm E2E đã chỉnh sửa"
        val editedMessage = "Lời nhắc đã chỉnh sửa qua giao diện E2E."
        val appSettings = runBlocking { repository.readSettings() }
        try {
            startHome()
            tap("group_${group.id}")
            editGroupName(renamed)
            eventually { snapshot() == baseline.copy(groups = baseline.groups.map { if (it.id == group.id) it.copy(name = renamed) else it }) }
            findText(renamed)
            capture("20-group-name-edited")
            editGroupName(group.name)
            eventually { snapshot() == baseline }

            tap("navigate_back")
            // Group and Home both contain app rows with the same semantic tags.
            // Wait until the outgoing group is removed before locating a Home row.
            assertTrue(device.wait(Until.gone(By.res("group_add_app")), 3_000))
            find("home_add")
            tap("app_${app.id}")
            tap("rule_${rule.id}")
            enter("rule_message", editedMessage)
            tap("rule_save")
            eventually { snapshot() == baseline.copy(rules = baseline.rules.map { if (it.id == rule.id) it.copy(customMessage = editedMessage) else it }) }
            findText("“$editedMessage”")
            capture("21-app-rule-message-edited")
            tap("rule_${rule.id}")
            enter("rule_message", rule.customMessage)
            tap("rule_save")
            eventually { snapshot() == baseline }

            tap("rule_menu_${rule.id}")
            tap("rule_delete_${rule.id}")
            eventually { snapshot() == baseline.copy(rules = baseline.rules.filterNot { it.id == rule.id }) }
            val undo = requireNotNull(device.wait(Until.findObject(By.text("Hoàn tác")), 3_000)) { "Rule deletion must offer Undo" }
            undo.click()
            eventually { snapshot() == baseline }
            tap("rule_${rule.id}")
            assertEquals("Undo retains the custom message", rule.customMessage, find("rule_message").text)
            tap("navigate_back")
            capture("22-app-rule-delete-undo-restored")
            assertEquals("UI edits and Undo preserve every original group, app and rule", baseline, snapshot())
            assertEquals("CRUD does not alter monitoring/onboarding", appSettings, runBlocking { repository.readSettings() })
        } catch (failure: Throwable) {
            capture("FAIL-rule-crud")
            throw failure
        } finally {
            // Failure cleanup only. Success above must already have restored state via real UI.
            if (snapshot() != baseline) runBlocking {
                repository.saveGroup(group)
                repository.saveRule(rule)
            }
            startHome()
        }
        assertEquals("The baseline is preserved for later visual/runtime tests", baseline, snapshot())
    }

    private fun editGroupName(name: String) {
        requireNotNull(device.wait(Until.findObject(By.desc("Tùy chọn nhóm")), 3_000)).click()
        findText("Chỉnh sửa nhóm").click()
        enter("group_name", name)
        tap("group_save")
        find("group_add_app")
    }

    private fun startHome() {
        val output = device.executeShellCommand("am start -W -f 0x10008000 -n ${context.packageName}/.MainActivity")
        check(!output.contains("Error:")) { output }
        find("home_add")
    }

    private fun tap(tag: String) {
        // Navigation can replace an accessibility node between lookup and click.
        // Only retry a stale lookup; never repeat an action that was delivered.
        repeat(3) { attempt ->
            try {
                find(tag).click()
                device.waitForIdle(700)
                return
            } catch (stale: StaleObjectException) {
                if (attempt == 2) throw stale
                device.waitForIdle(700)
            }
        }
    }
    private fun enter(tag: String, value: String) {
        val field = find(tag)
        field.click()
        device.waitForIdle(500)
        field.text = value
        device.pressBack()
        device.waitForIdle(500)
    }

    private fun find(tag: String): UiObject2 = locate("tag $tag") { device.findObject(By.res(tag)) }
    private fun findText(text: String): UiObject2 = locate("text $text") { device.findObject(By.text(text)) }
    private fun locate(description: String, lookup: () -> UiObject2?): UiObject2 {
        val initialEnd = SystemClock.elapsedRealtime() + 2_000
        while (SystemClock.elapsedRealtime() < initialEnd) {
            lookup()?.let { return it }
            SystemClock.sleep(100)
        }
        for (direction in listOf(Direction.DOWN, Direction.UP)) {
            repeat(9) {
                val scroll = device.findObject(By.scrollable(true))
                if (scroll != null) runCatching { scroll.scroll(direction, .65f) }
                else if (direction == Direction.DOWN) device.swipe(device.displayWidth / 2, device.displayHeight * 13 / 20, device.displayWidth / 2, device.displayHeight / 3, 25)
                else device.swipe(device.displayWidth / 2, device.displayHeight / 3, device.displayWidth / 2, device.displayHeight * 13 / 20, 25)
                SystemClock.sleep(450)
                device.waitForIdle(500)
                lookup()?.let { return it }
            }
        }
        error("Missing UI $description")
    }

    private fun eventually(check: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 8_000
        while (!check() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(100)
        assertTrue("Configuration did not reach the expected state", check())
    }

    private fun capture(name: String) {
        val directory = File(context.getExternalFilesDir(null), "e2e").apply { mkdirs() }
        device.takeScreenshot(File(directory, "$name.png"))
        device.dumpWindowHierarchy(File(directory, "$name.xml"))
    }
}
