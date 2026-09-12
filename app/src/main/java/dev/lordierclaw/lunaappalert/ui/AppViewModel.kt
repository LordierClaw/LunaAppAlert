package dev.lordierclaw.lunaappalert.ui

import android.app.Application
import android.graphics.drawable.Drawable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.lordierclaw.lunaappalert.AlertApplication
import dev.lordierclaw.lunaappalert.core.*
import dev.lordierclaw.lunaappalert.runtime.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class UiNotice(val text: String, val undo: (suspend () -> Unit)? = null)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as AlertApplication).repository
    private val systemAccess = SystemAccess(application)
    private val appCatalog = InstalledApps(application)
    val configuration = repository.configuration
    val settings = repository.settings
    val monitorState = MonitoringStatus.state
    private val _permissions = MutableStateFlow(PermissionState())
    val permissions = _permissions.asStateFlow()
    private val _installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val installedApps = _installedApps.asStateFlow()
    private val _appsLoading = MutableStateFlow(true)
    val appsLoading = _appsLoading.asStateFlow()
    private val _ready = MutableStateFlow(false)
    val ready = _ready.asStateFlow()
    private val notices = Channel<UiNotice>(Channel.BUFFERED)
    val messages = notices.receiveAsFlow()

    init {
        execute {
            val initial = repository.readSettings()
            settings.first { it == initial }
            val snapshot = repository.snapshot()
            configuration.first { it == snapshot }
            _ready.value = true
        }
    }

    fun refresh() {
        _permissions.value = systemAccess.permissions()
        execute {
            try { _installedApps.value = withContext(Dispatchers.IO) { appCatalog.list() } }
            finally { _appsLoading.value = false }
        }
        execute {
            val persisted = repository.readSettings()
            if (persisted.monitoringEnabled && permissions.value.usage) {
                MonitoringService.start(getApplication())
            } else if (!persisted.monitoringEnabled || !permissions.value.usage) {
                MonitoringService.stop(getApplication())
            }
        }
    }

    fun icon(packageName: String): Drawable? = appCatalog.icon(packageName)
    fun isInstalled(packageName: String): Boolean = appCatalog.isInstalled(packageName)
    fun usageIntent() = systemAccess.usageIntent()
    fun overlayIntent() = systemAccess.overlayIntent()
    fun batteryIntent() = systemAccess.batteryIntent()

    fun finishOnboarding(done: () -> Unit) = execute {
        repository.setOnboardingCompleted(true)
        repository.setMonitoringEnabled(true)
        if (permissions.value.usage) MonitoringService.start(getApplication())
        done()
    }

    fun setMonitoring(enabled: Boolean) = execute {
        repository.setMonitoringEnabled(enabled)
        if (enabled && permissions.value.usage) MonitoringService.start(getApplication())
        else MonitoringService.stop(getApplication())
    }

    fun toggleGroup(group: AppGroup, enabled: Boolean) = execute { repository.saveGroup(group.copy(enabled = enabled)) }
    fun toggleApp(app: TrackedApp, enabled: Boolean) = execute { repository.saveApp(app.copy(enabled = enabled)) }
    fun toggleRule(rule: Rule, enabled: Boolean) = execute { repository.saveRule(rule.copy(enabled = enabled)) }

    fun saveGroup(existing: AppGroup?, name: String, appIds: Set<String>, done: (String) -> Unit) = execute {
        val group = existing?.copy(name = name.trim()) ?: AppGroup(UUID.randomUUID().toString(), name.trim(), sortOrder = configuration.value.groups.size)
        repository.saveGroup(group)
        configuration.value.apps.forEach { app ->
            if (app.id in appIds && app.groupId != group.id) repository.moveApp(app.id, group.id)
            else if (app.groupId == group.id && app.id !in appIds) repository.moveApp(app.id, null)
        }
        done(group.id)
    }

    fun addApps(packages: Set<String>, groupId: String?, done: () -> Unit) = execute {
        val snapshot = configuration.value
        packages.forEachIndexed { index, packageName ->
            val existing = snapshot.apps.firstOrNull { it.packageName == packageName }
            if (existing != null) {
                if (groupId != null) repository.moveApp(existing.id, groupId)
            } else {
                val installed = installedApps.value.firstOrNull { it.packageName == packageName } ?: return@forEachIndexed
                repository.saveApp(TrackedApp(UUID.randomUUID().toString(), packageName, installed.label,
                    groupId = groupId, sortOrder = snapshot.apps.size + index))
            }
        }
        done()
    }

    fun moveApp(app: TrackedApp, groupId: String?) = execute {
        repository.moveApp(app.id, groupId)
        notices.send(UiNotice("Đã chuyển ${app.displayName}.") { repository.moveApp(app.id, app.groupId) })
    }

    fun saveRule(rule: Rule, done: () -> Unit) = execute {
        repository.saveRule(rule.normalized())
        done()
    }

    fun deleteRule(rule: Rule) = execute {
        val snapshot = configuration.value
        val app = snapshot.apps.firstOrNull { it.id == rule.ownerId }
        val inherited = rule.ownerType == OwnerType.APP && rule.enabled && app != null && RuleResolver.resolve(
            app, snapshot.copy(rules = snapshot.rules.filterNot { it.id == rule.id }), permissions.value,
            appCatalog.isInstalled(app.packageName), settings.value.monitoringEnabled,
        ).any { it.rule.ownerType == OwnerType.GROUP && it.rule.signature() == rule.signature() && it.status == RuleStatus.ACTIVE }
        repository.deleteRule(rule.id)
        val text = if (inherited) "Đã xóa. Quy tắc nhóm trùng khớp được áp dụng lại." else "Đã xóa quy tắc."
        notices.send(UiNotice(text) { repository.saveRule(rule) })
    }

    fun deleteGroup(group: AppGroup, done: () -> Unit) = execute {
        val snapshot = configuration.value
        repository.deleteGroup(group.id)
        done()
        notices.send(UiNotice("Đã xóa nhóm. Các ứng dụng được giữ lại.") {
            repository.saveGroup(group)
            snapshot.apps.filter { it.groupId == group.id }.forEach { repository.moveApp(it.id, group.id) }
            snapshot.rules.filter { it.ownerType == OwnerType.GROUP && it.ownerId == group.id }.forEach { repository.saveRule(it) }
        })
    }

    fun deleteApp(app: TrackedApp, done: () -> Unit) = execute {
        val rules = configuration.value.rules.filter { it.ownerType == OwnerType.APP && it.ownerId == app.id }
        repository.deleteApp(app.id)
        done()
        notices.send(UiNotice("Đã bỏ ${app.displayName} khỏi App Alert.") {
            repository.saveApp(app)
            rules.forEach { repository.saveRule(it) }
        })
    }

    fun undo(action: suspend () -> Unit) = execute { action() }
    fun notify(text: String) = execute { notices.send(UiNotice(text)) }
    private fun execute(block: suspend () -> Unit) = viewModelScope.launch {
        try { block() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { notices.send(UiNotice(error.message ?: "Không thể hoàn tất. Vui lòng thử lại.")) }
    }
}
