package dev.lordierclaw.lunaappalert

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import dev.lordierclaw.lunaappalert.ui.AccessAction
import dev.lordierclaw.lunaappalert.ui.AppAlertApp
import dev.lordierclaw.lunaappalert.ui.AppViewModel
import dev.lordierclaw.lunaappalert.ui.theme.LunaAppAlertTheme

class MainActivity : ComponentActivity() {
    private val appViewModel by lazy { ViewModelProvider(this)[AppViewModel::class.java] }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.WHITE, android.graphics.Color.WHITE))
        setContent {
            val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { appViewModel.refresh() }
            val access = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { appViewModel.refresh() }
            LunaAppAlertTheme {
                AppAlertApp(appViewModel) { action ->
                    try {
                        when (action) {
                            AccessAction.USAGE -> access.launch(appViewModel.usageIntent())
                            AccessAction.OVERLAY -> access.launch(appViewModel.overlayIntent())
                            AccessAction.BATTERY -> access.launch(appViewModel.batteryIntent())
                            AccessAction.NOTIFICATIONS -> {
                                if (Build.VERSION.SDK_INT >= 33 && !appViewModel.permissions.value.notifications &&
                                    !getPreferences(MODE_PRIVATE).getBoolean("notification_requested", false)) {
                                    getPreferences(MODE_PRIVATE).edit().putBoolean("notification_requested", true).apply()
                                    notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else access.launch(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
                            }
                        }
                    } catch (_: Exception) { appViewModel.notify("Không mở được cài đặt. Hãy kiểm tra quyền trong Cài đặt Android.") }
                }
            }
        }
    }

    override fun onResume() { super.onResume(); appViewModel.refresh() }
}
