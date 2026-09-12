package dev.lordierclaw.lunaappalert.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import androidx.room.withTransaction
import dev.lordierclaw.lunaappalert.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.IOException

private val Context.settingsStore by preferencesDataStore("settings")
data class AppSettings(val onboardingCompleted: Boolean = false, val monitoringEnabled: Boolean = false)

class DuplicateRuleException(val existingRuleId: String) : IllegalArgumentException("Đã có quy tắc với cùng điều kiện. Hãy sửa quy tắc hiện có.")

class AppRepository(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val database = Room.databaseBuilder(appContext, AlertDatabase::class.java, "app-alert.db").build()
    private val dao = database.dao()
    val configuration: StateFlow<Configuration> = combine(dao.groups(), dao.apps(), dao.rules()) { groups, apps, rules ->
        Configuration(groups.map { it.model() }, apps.map { it.model() }, rules.map { it.model() })
    }.stateIn(scope, SharingStarted.Eagerly, Configuration())
    private val onboarded = booleanPreferencesKey("onboarding_completed")
    private val monitoring = booleanPreferencesKey("monitoring_enabled")
    val settings: StateFlow<AppSettings> = appContext.settingsStore.data
        .catch { if (it is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw it }
        .map { AppSettings(it[onboarded] ?: false, it[monitoring] ?: false) }
        .stateIn(scope, SharingStarted.Eagerly, AppSettings())

    suspend fun readSettings(): AppSettings = appContext.settingsStore.data.first().let {
        AppSettings(it[onboarded] ?: false, it[monitoring] ?: false)
    }
    suspend fun snapshot(): Configuration = database.withTransaction {
        Configuration(dao.allGroups().map { it.model() }, dao.allApps().map { it.model() }, dao.allRules().map { it.model() })
    }
    suspend fun saveGroup(group: AppGroup) {
        require(group.name.isNotBlank()) { "Nhập tên nhóm." }
        dao.putGroup(group.entity())
    }
    suspend fun deleteGroup(id: String) = dao.deleteGroup(id)
    suspend fun saveApp(app: TrackedApp) = dao.putApp(app.entity())
    suspend fun deleteApp(id: String) = dao.deleteApp(id)
    suspend fun moveApp(appId: String, groupId: String?) = dao.moveApp(appId, groupId)
    suspend fun saveRule(rule: Rule) = database.withTransaction {
        require(rule.triggerType == TriggerType.ON_LAUNCH || (rule.thresholdMinutes ?: 0) > 0) { "Thời gian phải là số phút nguyên dương." }
        require(!rule.repeatEnabled || (rule.triggerType == TriggerType.CONTINUOUS_USE && (rule.repeatEveryMinutes ?: 0) > 0 && (rule.repeatMaxCount ?: 0) > 0)) { "Nhập khoảng lặp và số lần lặp hợp lệ." }
        val duplicate = dao.allRules().map { it.model() }.firstOrNull {
            it.id != rule.id && it.ownerType == rule.ownerType && it.ownerId == rule.ownerId && it.signature() == rule.signature()
        }
        if (duplicate != null) throw DuplicateRuleException(duplicate.id)
        dao.putRule(rule.entity())
    }
    suspend fun deleteRule(id: String) = dao.deleteRule(id)
    suspend fun setOnboardingCompleted(value: Boolean) { appContext.settingsStore.edit { it[onboarded] = value } }
    suspend fun setMonitoringEnabled(value: Boolean) { appContext.settingsStore.edit { it[monitoring] = value } }
    suspend fun readRuntime(): String? = dao.runtime()?.json
    suspend fun saveRuntime(json: String) = dao.putRuntime(RuntimeEntity(json = json))
}
