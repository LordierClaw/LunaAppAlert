package dev.lordierclaw.lunaappalert.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.lordierclaw.lunaappalert.core.*
import dev.lordierclaw.lunaappalert.runtime.RuntimeCheckpoint
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Isolated storage contracts: never opens AppRepository's production database or settings. */
@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    private lateinit var context: Context
    private lateinit var database: AlertDatabase
    private lateinit var dao: AlertDao
    private val group = AppGroup("group-a", "Mạng xã hội", enabled = false)
    private val app = TrackedApp("app-a", "example.test.app", "Ứng dụng thử", groupId = group.id)
    private val groupRule = Rule("group-rule", OwnerType.GROUP, group.id)
    private val appRule = Rule("app-rule", OwnerType.APP, app.id, enabled = false)

    @Before fun createIsolatedDatabase() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AlertDatabase::class.java).build()
        dao = database.dao()
    }

    @After fun closeIsolatedDatabase() { database.close() }

    private suspend fun seed() {
        dao.putGroup(group.entity())
        dao.putApp(app.entity())
        dao.putRule(groupRule.entity())
        dao.putRule(appRule.entity())
    }

    @Test fun deletingGroupMovesAppsToUngroupedAndPreservesAppRules() = runBlocking {
        seed()
        dao.deleteGroup(group.id)
        assertTrue(dao.allGroups().isEmpty())
        assertEquals(app.copy(groupId = null), dao.allApps().single().model())
        assertEquals(listOf(appRule.normalized()), dao.allRules().map { it.model() })
    }

    @Test fun movingAppChangesOnlyMembershipAndPreservesItsRulesAndToggles() = runBlocking {
        seed()
        val destination = AppGroup("group-b", "Nhóm khác", enabled = true)
        dao.putGroup(destination.entity())
        dao.moveApp(app.id, destination.id)
        assertEquals(app.copy(groupId = destination.id), dao.allApps().single().model())
        assertEquals(setOf(groupRule.normalized(), appRule.normalized()), dao.allRules().map { it.model() }.toSet())
        dao.moveApp(app.id, null)
        assertEquals(app.copy(groupId = null), dao.allApps().single().model())
        assertEquals(2, dao.allRules().size)
    }

    @Test fun parentTogglesDoNotRewriteChildRecords() = runBlocking {
        seed()
        val appsBefore = dao.allApps()
        val rulesBefore = dao.allRules()
        dao.putGroup(group.copy(enabled = true).entity())
        dao.putGroup(group.copy(enabled = false).entity())
        assertEquals(appsBefore, dao.allApps())
        assertEquals(rulesBefore, dao.allRules())
        dao.putApp(app.copy(enabled = false).entity())
        assertEquals(rulesBefore, dao.allRules())
    }

    @Test fun removingAppConfigurationCascadesOnlyItsRulesAndDoesNotUninstallPackage() = runBlocking {
        seed()
        val installed = app.copy(packageName = context.packageName)
        dao.putApp(installed.entity())
        @Suppress("DEPRECATION")
        val beforePackage = context.packageManager.getPackageInfo(context.packageName, 0).packageName
        dao.deleteApp(app.id)
        assertTrue(dao.allApps().isEmpty())
        assertEquals(listOf(groupRule.normalized()), dao.allRules().map { it.model() })
        assertEquals(listOf(group), dao.allGroups().map { it.model() })
        @Suppress("DEPRECATION")
        val afterPackage = context.packageManager.getPackageInfo(context.packageName, 0).packageName
        assertEquals(beforePackage, afterPackage)
    }

    @Test fun databaseCannotPersistDuplicateSignaturesEvenWhenOriginalIsDisabled() = runBlocking {
        seed()
        // Room's upsert may reject the secondary unique key or leave no inserted row;
        // the invariant is that a second signature never reaches persistent state.
        runCatching { dao.putRule(appRule.copy(id = "duplicate-app", enabled = true).entity()) }
        dao.putRule(groupRule.copy(enabled = false).entity())
        runCatching { dao.putRule(groupRule.copy(id = "duplicate-group", enabled = true).entity()) }
        val rules = dao.allRules().map { it.model() }
        assertEquals(2, rules.size)
        assertFalse(rules.single { it.id == appRule.id }.enabled)
        assertFalse(rules.single { it.id == groupRule.id }.enabled)
        assertEquals(1, rules.count { it.ownerType == OwnerType.APP && it.signature() == appRule.signature() })
        assertEquals(1, rules.count { it.ownerType == OwnerType.GROUP && it.signature() == groupRule.signature() })
    }

    @Test fun packageIdentityIsUniqueWhileDifferentRuleThresholdsCoexist() = runBlocking {
        seed()
        runCatching { dao.putApp(app.copy(id = "duplicate-package").entity()) }
        assertEquals(1, dao.allApps().count { it.packageName == app.packageName })
        dao.putRule(appRule.copy(id = "different-threshold", thresholdMinutes = 45).entity())
        assertEquals(setOf(20, 45), dao.allRules().map { it.model() }.filter { it.ownerType == OwnerType.APP }.map { it.thresholdMinutes }.toSet())
    }

    @Test fun runtimeCheckpointRoundTripsThroughRealAndroidJsonAndRoom() = runBlocking {
        val checkpoint = RuntimeCheckpoint(8, 1_800_000_000_000L,
            EvaluationState("2026-09-12", mapOf("5:ứngụrule" to true), "session-1", mapOf("rule-key" to 3)),
            SessionCheckpoint("example.app", "session-1", 10_000, 70_000))
        dao.putRuntime(RuntimeEntity(json = checkpoint.encode()))
        assertEquals(checkpoint, RuntimeCheckpoint.decode(dao.runtime()?.json))
        val withoutSession = checkpoint.copy(evaluation = checkpoint.evaluation.copy(sessionId = null,
            sessionOccurrences = emptyMap()), session = null)
        assertEquals(withoutSession, RuntimeCheckpoint.decode(withoutSession.encode()))
        val futureCompatible = JSONObject(checkpoint.encode()).put("unrecognizedFutureField", "ignored").toString()
        assertEquals(checkpoint, RuntimeCheckpoint.decode(futureCompatible))
    }

    @Test fun malformedRuntimeCheckpointRecoversWithoutThrowingOrChangingConfiguration() = runBlocking {
        seed()
        for (malformed in listOf(null, "", "not json", "{}", "{\"boot\":8,\"wall\":1,\"session\":{}}")) {
            assertNull(RuntimeCheckpoint.decode(malformed))
        }
        dao.putRuntime(RuntimeEntity(json = "corrupt"))
        assertNull(RuntimeCheckpoint.decode(dao.runtime()?.json))
        assertEquals(app, dao.allApps().single().model())
        assertEquals(2, dao.allRules().size)
    }

    @Test fun settingsPersistWhenAnIsolatedPreferenceStoreIsClosedAndReopened() = runBlocking {
        val file = File(context.cacheDir, "repository-test-${UUID.randomUUID()}.preferences_pb")
        val onboarded = booleanPreferencesKey("onboarding_completed")
        val monitoring = booleanPreferencesKey("monitoring_enabled")
        val firstJob = SupervisorJob()
        val secondJob = SupervisorJob()
        try {
            val first = PreferenceDataStoreFactory.create(scope = CoroutineScope(firstJob + Dispatchers.IO), produceFile = { file })
            first.edit { it[onboarded] = true; it[monitoring] = false }
            firstJob.cancelAndJoin()
            val reopened = PreferenceDataStoreFactory.create(scope = CoroutineScope(secondJob + Dispatchers.IO), produceFile = { file })
            val saved = reopened.data.first()
            assertEquals(AppSettings(onboardingCompleted = true, monitoringEnabled = false),
                AppSettings(saved[onboarded] ?: false, saved[monitoring] ?: true))
        } finally {
            firstJob.cancelAndJoin()
            secondJob.cancelAndJoin()
            file.delete()
        }
    }
}
