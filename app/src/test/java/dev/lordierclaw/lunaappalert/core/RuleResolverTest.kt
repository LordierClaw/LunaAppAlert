package dev.lordierclaw.lunaappalert.core

import org.junit.Assert.*
import org.junit.Test

class RuleResolverTest {
    private val group = AppGroup("group", "Mạng xã hội")
    private val app = TrackedApp("app", "example.app", "Ứng dụng", groupId = group.id)
    private val permissions = PermissionState(true, true, true)
    private fun rule(id: String, owner: OwnerType, type: TriggerType = TriggerType.CONTINUOUS_USE,
                     minutes: Int? = 20, enabled: Boolean = true) =
        Rule(id, owner, if (owner == OwnerType.GROUP) group.id else app.id,
            enabled = enabled, triggerType = type, thresholdMinutes = minutes)
    private fun resolve(vararg rules: Rule) = RuleResolver.resolve(app,
        Configuration(listOf(group), listOf(app), rules.toList()), permissions)

    @Test fun launchPrecedenceMatrix() {
        for (groupOn in listOf(false, true)) for (appOn in listOf(false, true)) {
            val result = resolve(rule("g", OwnerType.GROUP, TriggerType.ON_LAUNCH, enabled = groupOn),
                rule("a", OwnerType.APP, TriggerType.ON_LAUNCH, enabled = appOn))
            val expected = if (appOn) listOf("a") else if (groupOn) listOf("g") else emptyList()
            assertEquals(expected, result.filter { it.status == RuleStatus.ACTIVE }.map { it.rule.id })
        }
    }

    @Test fun equalContinuousThresholdOverridesOnlyMatchingGroupRule() {
        val result = resolve(rule("g20", OwnerType.GROUP), rule("g45", OwnerType.GROUP, minutes = 45),
            rule("a20", OwnerType.APP))
        assertEquals(RuleStatus.OVERRIDDEN, result.first { it.rule.id == "g20" }.status)
        assertEquals(setOf("g45", "a20"), result.filter { it.status == RuleStatus.ACTIVE }.map { it.rule.id }.toSet())
        assertEquals(group.name, result.first().sourceGroupName)
        assertNull(result.last().sourceGroupName)
    }

    @Test fun differentThresholdsAndTriggerFamiliesCoexist() {
        val result = resolve(rule("g20", OwnerType.GROUP), rule("a45", OwnerType.APP, minutes = 45),
            rule("daily20", OwnerType.APP, TriggerType.DAILY_TOTAL))
        assertTrue(result.all { it.status == RuleStatus.ACTIVE })
    }

    @Test fun dailyPrecedenceUsesThreshold() {
        val equal = resolve(rule("g", OwnerType.GROUP, TriggerType.DAILY_TOTAL, 60),
            rule("a", OwnerType.APP, TriggerType.DAILY_TOTAL, 60))
        assertEquals(listOf(RuleStatus.OVERRIDDEN, RuleStatus.ACTIVE), equal.map { it.status })
        assertTrue(resolve(rule("g", OwnerType.GROUP, TriggerType.DAILY_TOTAL, 30),
            rule("a", OwnerType.APP, TriggerType.DAILY_TOTAL, 60)).all { it.status == RuleStatus.ACTIVE })
    }

    @Test fun disabledAppRuleDoesNotOverride() {
        assertEquals(listOf(RuleStatus.ACTIVE, RuleStatus.OFF),
            resolve(rule("g", OwnerType.GROUP), rule("a", OwnerType.APP, enabled = false)).map { it.status })
    }

    @Test fun overrideIsIndependentOfDeliveryRepeatAndMessageAndMissingPermission() {
        val rules = listOf(rule("g", OwnerType.GROUP).copy(alertType = AlertType.NOTIFICATION),
            rule("a", OwnerType.APP).copy(repeatEnabled = true, customMessage = "Khác"))
        val result = RuleResolver.resolve(app, Configuration(listOf(group), listOf(app), rules),
            permissions.copy(overlay = false))
        assertEquals(listOf(RuleStatus.OVERRIDDEN, RuleStatus.NEEDS_PERMISSION), result.map { it.status })
    }

    @Test fun parentGatePreservesAllStoredChildStates() {
        val config = Configuration(listOf(group.copy(enabled = false)), listOf(app),
            listOf(rule("g", OwnerType.GROUP), rule("a", OwnerType.APP, minutes = 45)))
        assertTrue(RuleResolver.resolve(app, config, permissions).all { it.status == RuleStatus.PAUSED_BY_GROUP })
        assertTrue(config.apps.single().enabled)
        assertTrue(config.rules.all { it.enabled })
        assertTrue(RuleResolver.resolve(app, config.copy(groups = listOf(group)), permissions)
            .all { it.status == RuleStatus.ACTIVE })
    }

    @Test fun appPermissionInstalledAndMonitoringGatesRemainDistinct() {
        val config = Configuration(listOf(group), listOf(app), listOf(rule("g", OwnerType.GROUP)))
        assertEquals(RuleStatus.PAUSED_BY_APP, RuleResolver.resolve(app.copy(enabled = false), config, permissions).single().status)
        assertEquals(RuleStatus.NEEDS_PERMISSION, RuleResolver.resolve(app, config, permissions.copy(usage = false)).single().status)
        assertEquals(RuleStatus.NOT_INSTALLED, RuleResolver.resolve(app, config, permissions, installed = false).single().status)
        assertEquals(RuleStatus.MONITORING_PAUSED, RuleResolver.resolve(app, config, permissions, monitoringEnabled = false).single().status)
        assertTrue(config.rules.single().enabled)
    }

    @Test fun missingOverlayPermissionDoesNotPreventNotificationRule() {
        val config = Configuration(listOf(group), listOf(app), listOf(rule("g", OwnerType.GROUP).copy(alertType = AlertType.NOTIFICATION),
            rule("a", OwnerType.APP, minutes = 45)))
        assertEquals(listOf(RuleStatus.ACTIVE, RuleStatus.NEEDS_PERMISSION),
            RuleResolver.resolve(app, config, permissions.copy(overlay = false)).map { it.status })
    }

    @Test fun movingAppChangesInheritedRulesWithoutChangingOwnRules() {
        val other = AppGroup("other", "Nhóm khác")
        val config = Configuration(listOf(group, other), listOf(app), listOf(rule("g", OwnerType.GROUP),
            rule("a", OwnerType.APP, minutes = 45), rule("other-rule", OwnerType.GROUP, minutes = 10).copy(ownerId = other.id)))
        assertEquals(setOf("other-rule", "a"), RuleResolver.resolve(app.copy(groupId = other.id), config, permissions).map { it.rule.id }.toSet())
        assertEquals(listOf("a"), RuleResolver.resolve(app.copy(groupId = null), config, permissions).map { it.rule.id })
    }

    @Test fun deletingOverrideReactivatesGroupAndDoesNotAffectAnotherApp() {
        val own = rule("a", OwnerType.APP)
        val inherited = rule("g", OwnerType.GROUP)
        assertEquals(RuleStatus.ACTIVE, resolve(inherited).single().status)
        val otherApp = app.copy(id = "other-app", packageName = "other.package")
        val config = Configuration(listOf(group), listOf(app, otherApp), listOf(inherited, own))
        assertEquals(RuleStatus.ACTIVE, RuleResolver.resolve(otherApp, config, permissions).single().status)
    }

    @Test fun duplicateValidationIncludesDisabledRulesAndExcludesCurrentEdit() {
        val existing = rule("existing", OwnerType.APP, enabled = false)
        assertTrue(RuleValidator.validate(existing.copy(id = "new"), listOf(existing)).any { it.field == "duplicate" })
        assertTrue(RuleValidator.validate(existing, listOf(existing)).isEmpty())
        assertTrue(RuleValidator.validate(existing.copy(id = "group", ownerType = OwnerType.GROUP, ownerId = group.id), listOf(existing)).isEmpty())
    }

    @Test fun invalidTimesRejectedAndLaunchSignatureIgnoresTimeFields() {
        val invalid = rule("invalid", OwnerType.APP, minutes = 0).copy(repeatEnabled = true,
            repeatEveryMinutes = -1, repeatMaxCount = null)
        assertEquals(setOf("thresholdMinutes", "repeatEveryMinutes", "repeatMaxCount"),
            RuleValidator.validate(invalid).map { it.field }.toSet())
        assertEquals(rule("a", OwnerType.APP, TriggerType.ON_LAUNCH, 20).signature(),
            rule("b", OwnerType.APP, TriggerType.ON_LAUNCH, 45).signature())
        val normalized = invalid.copy(triggerType = TriggerType.ON_LAUNCH).normalized()
        assertNull(normalized.thresholdMinutes)
        assertFalse(normalized.repeatEnabled)
        assertTrue(RuleValidator.validate(normalized).isEmpty())
    }
}
