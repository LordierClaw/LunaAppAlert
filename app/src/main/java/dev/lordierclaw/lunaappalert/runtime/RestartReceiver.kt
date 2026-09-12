package dev.lordierclaw.lunaappalert.runtime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.lordierclaw.lunaappalert.AlertApplication
import kotlinx.coroutines.*

class RestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED,Intent.ACTION_MY_PACKAGE_REPLACED)) return
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repository = (context.applicationContext as AlertApplication).repository
                if (repository.readSettings().monitoringEnabled && SystemAccess(context).permissions().usage)
                    MonitoringService.start(context)
            } finally { result.finish() }
        }
    }
}
