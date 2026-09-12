package dev.lordierclaw.lunaappalert.core

enum class OwnerType { GROUP, APP }
enum class TriggerType { ON_LAUNCH, CONTINUOUS_USE, DAILY_TOTAL }
enum class AlertType { NOTIFICATION, OVERLAY }

data class AppGroup(
    val id: String,
    val name: String,
    val enabled: Boolean = true,
    val sortOrder: Int = 0,
)

data class TrackedApp(
    val id: String,
    val packageName: String,
    val displayName: String,
    val enabled: Boolean = true,
    val groupId: String? = null,
    val sortOrder: Int = 0,
)

data class Rule(
    val id: String,
    val ownerType: OwnerType,
    val ownerId: String,
    val enabled: Boolean = true,
    val triggerType: TriggerType = TriggerType.CONTINUOUS_USE,
    val thresholdMinutes: Int? = 20,
    val repeatEnabled: Boolean = false,
    val repeatEveryMinutes: Int? = 5,
    val repeatMaxCount: Int? = 3,
    val alertType: AlertType = AlertType.OVERLAY,
    val customMessage: String = "",
)

data class Configuration(
    val groups: List<AppGroup> = emptyList(),
    val apps: List<TrackedApp> = emptyList(),
    val rules: List<Rule> = emptyList(),
)

data class PermissionState(
    val usage: Boolean = false,
    val overlay: Boolean = false,
    val notifications: Boolean = false,
)

enum class RuleStatus {
    ACTIVE, OFF, PAUSED_BY_GROUP, PAUSED_BY_APP, NEEDS_PERMISSION, OVERRIDDEN,
    NOT_INSTALLED, MONITORING_PAUSED,
}

data class ResolvedRule(
    val rule: Rule,
    val sourceGroupName: String?,
    val status: RuleStatus,
)

data class RuleSignature(val triggerType: TriggerType, val thresholdMinutes: Int?)

fun Rule.signature(): RuleSignature = RuleSignature(
    triggerType,
    thresholdMinutes.takeUnless { triggerType == TriggerType.ON_LAUNCH },
)

fun ruleSignature(rule: Rule): RuleSignature = rule.signature()
