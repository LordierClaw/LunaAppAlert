package dev.lordierclaw.lunaappalert

import android.app.Application
import dev.lordierclaw.lunaappalert.data.AppRepository

class AlertApplication : Application() {
    val repository by lazy { AppRepository(this) }
}
