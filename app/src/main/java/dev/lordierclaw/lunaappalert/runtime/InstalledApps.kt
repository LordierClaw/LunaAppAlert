package dev.lordierclaw.lunaappalert.runtime

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import java.text.Collator
import java.util.Locale

data class InstalledApp(val packageName: String, val label: String, val icon: Drawable)

class InstalledApps(private val context: Context) {
    private val manager get() = context.packageManager
    @Suppress("DEPRECATION")
    fun list(): List<InstalledApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val collator = Collator.getInstance(Locale.forLanguageTag("vi"))
        return manager.queryIntentActivities(intent, 0).filter { it.activityInfo.packageName != context.packageName }
            .distinctBy { it.activityInfo.packageName }.map {
                InstalledApp(it.activityInfo.packageName, it.loadLabel(manager).toString(), it.loadIcon(manager))
            }.sortedWith { a, b -> collator.compare(a.label, b.label) }
    }
    @Suppress("DEPRECATION")
    fun isInstalled(packageName: String): Boolean = try { manager.getApplicationInfo(packageName, 0).enabled } catch (_: PackageManager.NameNotFoundException) { false }
    fun icon(packageName: String): Drawable? = try { manager.getApplicationIcon(packageName) } catch (_: PackageManager.NameNotFoundException) { null }
}
