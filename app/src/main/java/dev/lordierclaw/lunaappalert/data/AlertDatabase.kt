package dev.lordierclaw.lunaappalert.data

import androidx.room.*
import dev.lordierclaw.lunaappalert.core.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "groups")
data class GroupEntity(@PrimaryKey val id: String, val name: String, val enabled: Boolean, val sortOrder: Int) {
    fun model() = AppGroup(id, name, enabled, sortOrder)
}

@Entity(tableName = "apps", indices = [Index(value = ["packageName"], unique = true), Index("groupId")],
    foreignKeys = [ForeignKey(entity = GroupEntity::class, parentColumns = ["id"], childColumns = ["groupId"], onDelete = ForeignKey.SET_NULL)])
data class AppEntity(@PrimaryKey val id: String, val packageName: String, val displayName: String,
                     val enabled: Boolean, val groupId: String?, val sortOrder: Int) {
    fun model() = TrackedApp(id, packageName, displayName, enabled, groupId, sortOrder)
}

@Entity(tableName = "rules", indices = [Index(value = ["groupOwnerId", "signature"], unique = true),
    Index(value = ["appOwnerId", "signature"], unique = true)], foreignKeys = [
    ForeignKey(entity = GroupEntity::class, parentColumns = ["id"], childColumns = ["groupOwnerId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = AppEntity::class, parentColumns = ["id"], childColumns = ["appOwnerId"], onDelete = ForeignKey.CASCADE)])
data class RuleEntity(@PrimaryKey val id: String, val groupOwnerId: String?, val appOwnerId: String?,
    val enabled: Boolean, val triggerType: String, val thresholdMinutes: Int?, val repeatEnabled: Boolean,
    val repeatEveryMinutes: Int?, val repeatMaxCount: Int?, val alertType: String, val customMessage: String, val signature: String) {
    fun model() = Rule(id, if (groupOwnerId != null) OwnerType.GROUP else OwnerType.APP,
        groupOwnerId ?: requireNotNull(appOwnerId), enabled, TriggerType.valueOf(triggerType), thresholdMinutes,
        repeatEnabled, repeatEveryMinutes, repeatMaxCount, AlertType.valueOf(alertType), customMessage)
}

@Entity(tableName = "runtime")
data class RuntimeEntity(@PrimaryKey val id: Int = 0, val json: String)

@Dao
interface AlertDao {
    @Query("SELECT * FROM groups ORDER BY sortOrder, name") fun groups(): Flow<List<GroupEntity>>
    @Query("SELECT * FROM apps ORDER BY sortOrder, displayName") fun apps(): Flow<List<AppEntity>>
    @Query("SELECT * FROM rules ORDER BY rowid") fun rules(): Flow<List<RuleEntity>>
    @Query("SELECT * FROM groups ORDER BY sortOrder, name") suspend fun allGroups(): List<GroupEntity>
    @Query("SELECT * FROM apps ORDER BY sortOrder, displayName") suspend fun allApps(): List<AppEntity>
    @Query("SELECT * FROM rules ORDER BY rowid") suspend fun allRules(): List<RuleEntity>
    @Upsert suspend fun putGroup(value: GroupEntity)
    @Upsert suspend fun putApp(value: AppEntity)
    @Upsert suspend fun putRule(value: RuleEntity)
    @Query("DELETE FROM groups WHERE id = :id") suspend fun deleteGroup(id: String)
    @Query("DELETE FROM apps WHERE id = :id") suspend fun deleteApp(id: String)
    @Query("DELETE FROM rules WHERE id = :id") suspend fun deleteRule(id: String)
    @Query("UPDATE apps SET groupId = :groupId WHERE id = :appId") suspend fun moveApp(appId: String, groupId: String?)
    @Query("SELECT * FROM runtime WHERE id = 0") suspend fun runtime(): RuntimeEntity?
    @Upsert suspend fun putRuntime(value: RuntimeEntity)
}

@Database(entities = [GroupEntity::class, AppEntity::class, RuleEntity::class, RuntimeEntity::class], version = 1, exportSchema = true)
abstract class AlertDatabase : RoomDatabase() {
    abstract fun dao(): AlertDao
}

fun AppGroup.entity() = GroupEntity(id, name.trim(), enabled, sortOrder)
fun TrackedApp.entity() = AppEntity(id, packageName, displayName, enabled, groupId, sortOrder)
fun Rule.entity(): RuleEntity {
    val normalized = if (triggerType == TriggerType.ON_LAUNCH) copy(thresholdMinutes = null, repeatEnabled = false,
        repeatEveryMinutes = null, repeatMaxCount = null) else if (triggerType == TriggerType.DAILY_TOTAL || !repeatEnabled)
        copy(repeatEnabled = false, repeatEveryMinutes = null, repeatMaxCount = null) else this
    return with(normalized) { RuleEntity(id, ownerId.takeIf { ownerType == OwnerType.GROUP },
        ownerId.takeIf { ownerType == OwnerType.APP }, enabled, triggerType.name, thresholdMinutes, repeatEnabled,
        repeatEveryMinutes, repeatMaxCount, alertType.name, customMessage.trim(), "${triggerType.name}:${signature().thresholdMinutes ?: 0}") }
}
