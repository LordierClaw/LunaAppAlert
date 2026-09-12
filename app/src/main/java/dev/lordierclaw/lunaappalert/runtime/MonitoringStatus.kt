package dev.lordierclaw.lunaappalert.runtime

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class MonitorState(val running: Boolean = false, val message: String = "Chưa bật theo dõi")
object MonitoringStatus {
    private val mutable = MutableStateFlow(MonitorState())
    val state: StateFlow<MonitorState> = mutable
    internal fun update(running: Boolean, message: String) { mutable.value = MonitorState(running, message) }
}
