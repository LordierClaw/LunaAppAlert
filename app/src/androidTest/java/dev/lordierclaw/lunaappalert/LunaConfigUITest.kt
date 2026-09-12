package dev.lordierclaw.lunaappalert

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import dev.lordierclaw.lunaappalert.core.OwnerType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Additional real UI coverage, executed after the main journey with its configuration intact. */
@RunWith(AndroidJUnit4::class)
class LunaConfigUITest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val repository get() = (context.applicationContext as AlertApplication).repository
    private fun snapshot() = runBlocking { repository.snapshot() }

    @Test fun hierarchyGatesMoveUndoDuplicateAndGroupDeletion() {
        // A previous interrupted invocation may have left only its temporary group.
        // Restore those named fixture rows before taking this run's baseline.
        val previous = snapshot()
        val fixtureGroup = previous.groups.single { it.name == "Nhóm E2E" }
        runBlocking {
            previous.groups.filter { it.name == "Nhóm tạm E2E" }.forEach { temporary ->
                previous.apps.filter { it.groupId == temporary.id }.forEach { repository.moveApp(it.id, fixtureGroup.id) }
                repository.deleteGroup(temporary.id)
            }
        }
        val original = snapshot()
        assertEquals(1, original.groups.size)
        assertEquals(4, original.rules.size)
        val originalGroup = original.groups.single { it.name == "Nhóm E2E" }
        val alpha = original.apps.single { it.packageName == "dev.lordierclaw.fixture.alpha" }
        val beta = original.apps.single { it.packageName == "dev.lordierclaw.fixture.beta" }
        openHome()
        tap("app_toggle_${alpha.id}")
        eventually { !snapshot().apps.single { it.id == alpha.id }.enabled }
        tap("group_toggle_${originalGroup.id}")
        eventually { !snapshot().groups.single { it.id == originalGroup.id }.enabled }
        assertFalse("Group pause preserves A's saved disabled state", snapshot().apps.single { it.id == alpha.id }.enabled)
        assertTrue("Group pause preserves B's saved enabled state", snapshot().apps.single { it.id == beta.id }.enabled)
        capture("10-hierarchy-gates")
        tap("group_toggle_${originalGroup.id}")
        tap("app_toggle_${alpha.id}")
        eventually { snapshot() == original }

        tap("home_add"); tap("menu_create_group")
        enter("group_name", "Nhóm tạm E2E")
        tap("group_save")
        eventually { snapshot().groups.size == 2 }
        val temporary = snapshot().groups.single { it.name == "Nhóm tạm E2E" }
        openHome(); tap("app_${alpha.id}")
        moveTo(temporary.id)
        eventually { snapshot().apps.single { it.id == alpha.id }.groupId == temporary.id }
        requireNotNull(device.wait(Until.findObject(By.text("Hoàn tác")), 4_000)).click()
        eventually { snapshot().apps.single { it.id == alpha.id }.groupId == originalGroup.id }
        assertEquals("Undo move retains own app rules", original.rules, snapshot().rules)
        capture("11-move-undo")

        // A second ON_LAUNCH signature under A must be rejected through the editor.
        tap("add_app_rule"); tap("trigger_ON_LAUNCH"); tap("rule_save")
        findText("Đã có quy tắc trùng khớp")
        assertEquals("Duplicate rule was not inserted", original.rules, snapshot().rules)
        capture("12-duplicate-rule-validation")
        tap("navigate_back")
        requireNotNull(device.wait(Until.findObject(By.text("Bỏ thay đổi")), 3_000)).click()
        moveTo(temporary.id)
        eventually { snapshot().apps.single { it.id == alpha.id }.groupId == temporary.id }
        openHome(); tap("group_${temporary.id}")
        tap("add_group_rule"); tap("trigger_ON_LAUNCH")
        enter("rule_message", "E2E quy tắc nhóm tạm")
        tap("rule_save")
        eventually { snapshot().rules.size == 5 }
        requireNotNull(device.wait(Until.findObject(By.desc("Tùy chọn nhóm")), 5_000)).click()
        requireNotNull(device.wait(Until.findObject(By.text("Xóa nhóm")), 3_000)).click()
        tap("confirm_delete_group")
        eventually { snapshot().groups.none { it.id == temporary.id } }
        val afterDelete = snapshot()
        assertEquals("Deleting a group retains all configured applications", original.apps.map { it.id }.toSet(), afterDelete.apps.map { it.id }.toSet())
        assertNull("Former group member becomes ungrouped", afterDelete.apps.single { it.id == alpha.id }.groupId)
        assertEquals("Deleting a group retains own app rules", original.rules.filter { it.ownerType == OwnerType.APP }, afterDelete.rules.filter { it.ownerType == OwnerType.APP })
        assertFalse("Only deleted group's rules are removed", afterDelete.rules.any { it.ownerId == temporary.id })
        capture("13-group-deletion-preserves-apps")
        openHome(); tap("app_${alpha.id}"); moveTo(originalGroup.id)
        eventually { snapshot() == original }
        openHome()
    }

    private fun moveTo(groupId: String) {
        tap("app_menu"); tap("move_app"); tap("move_target_$groupId"); tap("confirm_move")
    }
    private fun openHome() {
        device.executeShellCommand("am start -W -f 0x10008000 -n ${context.packageName}/.MainActivity")
        requireNotNull(device.wait(Until.findObject(By.res("home_add")), 10_000)) { "Home unavailable" }
    }
    private fun find(tag: String): UiObject2 {
        device.wait(Until.findObject(By.res(tag)), 2_000)?.let { return it }
        repeat(8) {
            scrollContent()
            SystemClock.sleep(450)
            device.waitForIdle(1_000)
            device.wait(Until.findObject(By.res(tag)), 300)?.let { return it }
        }
        error("Missing UI tag $tag")
    }
    private fun findText(text: String): UiObject2 {
        device.wait(Until.findObject(By.text(text)), 2_000)?.let { return it }
        repeat(8) {
            scrollContent()
            SystemClock.sleep(450)
            device.waitForIdle(1_000)
            device.wait(Until.findObject(By.text(text)), 300)?.let { return it }
        }
        error("Missing UI text $text")
    }
    private fun tap(tag: String) { find(tag).click(); device.waitForIdle(700) }
    private fun scrollContent() {
        val area = device.findObject(By.scrollable(true))?.visibleBounds
        val top = area?.top ?: device.displayHeight / 5
        val bottom = area?.bottom ?: device.displayHeight * 3 / 4
        device.swipe(device.displayWidth / 2, top + (bottom - top) * 4 / 5,
            device.displayWidth / 2, top + (bottom - top) / 4, 25)
    }
    private fun enter(tag: String, value: String) {
        val field = find(tag); field.click(); SystemClock.sleep(450); field.text = value
        SystemClock.sleep(300)
        repeat(3) {
            val ime = device.executeShellCommand("dumpsys input_method")
            if (!ime.contains("mInputShown=true")) return
            device.pressBack(); SystemClock.sleep(450)
        }
    }
    private fun eventually(check: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + 8_000
        while (!check() && SystemClock.elapsedRealtime() < end) SystemClock.sleep(100)
        assertTrue("Configuration did not reach expected state", check())
    }
    private fun capture(name: String) {
        val directory = File(context.getExternalFilesDir(null), "e2e").apply { mkdirs() }
        device.takeScreenshot(File(directory, "$name.png"))
        device.dumpWindowHierarchy(File(directory, "$name.xml"))
    }
}
