package dev.lordierclaw.lunaappalert.runtime

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.lordierclaw.lunaappalert.core.PermissionState

class SystemAccess(private val context: Context) {
    fun permissions(): PermissionState {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= 29) appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        else @Suppress("DEPRECATION") appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        val notificationPermission = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return PermissionState(mode == AppOpsManager.MODE_ALLOWED, Settings.canDrawOverlays(context),
            notificationPermission && NotificationManagerCompat.from(context).areNotificationsEnabled() &&
                (context.getSystemService(android.app.NotificationManager::class.java).getNotificationChannel(AlertDispatcher.ALERT_CHANNEL)?.importance != android.app.NotificationManager.IMPORTANCE_NONE))
    }
    fun usageIntent(): Intent {
        val direct = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:${context.packageName}"))
        // Android 8's Settings only registers the package-less action. Capability
        // detection also handles vendor Settings apps without the direct route.
        return if (direct.resolveActivity(context.packageManager) != null) direct
        else Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
    }
    fun overlayIntent() = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
    fun batteryIntent() = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
}
