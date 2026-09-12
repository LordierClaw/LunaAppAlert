package dev.lordierclaw.lunaappalert

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class LunaOverlayTest {
    @Test fun overlayRespectsNativeStatusBarAndLargeText() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val directory = File(context.getExternalFilesDir(null), "visual").apply { mkdirs() }
        val fontScale = device.executeShellCommand("settings get system font_scale").trim()
        try {
            device.executeShellCommand("am start -W -n ${context.packageName}/.MainActivity")
            SystemClock.sleep(2_000)
            device.executeShellCommand("settings put system font_scale 2.0")
            device.pressHome()
            SystemClock.sleep(1_500)
            device.executeShellCommand("am start -W -n dev.lordierclaw.fixture.beta/dev.lordierclaw.fixture.MainActivity")
            val overlay = requireNotNull(device.wait(Until.findObject(By.desc("overlay_alert")), 12_000))
            device.takeScreenshot(File(directory, "09-overlay-large-text.png"))
            device.dumpWindowHierarchy(File(directory, "09-overlay-large-text.xml"))
            val status = device.findObject(By.res("com.android.systemui", "status_bar"))
            assertNotNull("Native Android status bar remains present", status)
            assertTrue("Overlay must not paint over the native status bar's background",
                overlay.visibleBounds.top >= requireNotNull(status).visibleBounds.bottom)
            val continueButton = requireNotNull(device.findObject(By.desc("overlay_continue")))
            assertTrue(continueButton.isClickable && continueButton.isEnabled)
            assertTrue(continueButton.visibleBounds.height() > 0)
            continueButton.click()
            assertNull("Continue closes the overlay without opening a new session",
                device.wait(Until.findObject(By.desc("overlay_alert")), 3_000))
        } finally {
            device.pressHome()
            device.executeShellCommand("settings put system font_scale $fontScale")
            device.executeShellCommand("am start -W -n ${context.packageName}/.MainActivity")
        }
    }
}
