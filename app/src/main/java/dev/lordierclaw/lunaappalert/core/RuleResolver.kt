package dev.lordierclaw.lunaappalert.core

/** The only place that decides ownership precedence and effective rule status. */
object RuleResolver {
    fun resolve(
        app: TrackedApp,
        configuration: Configuration,
        permissions: PermissionState,
        installed: Boolean = true,
        monitoringEnabled: Boolean = true,
    ): List<ResolvedRule> {
        val group = configuration.groups.firstOrNull { it.id == app.groupId }
        val ownRules = configuration.rules.filter {
            it.ownerType == OwnerType.APP && it.ownerId == app.id
        }
        val overridingSignatures = ownRules.filter { it.enabled }.map { it.signature() }.toSet()
        val groupRules = configuration.rules.filter {
            group != null && it.ownerType == OwnerType.GROUP && it.ownerId == group.id
        }
        return (groupRules + ownRules).map { rule ->
            val inherited = rule.ownerType == OwnerType.GROUP
            val status = when {
                !rule.enabled -> RuleStatus.OFF
                !monitoringEnabled -> RuleStatus.MONITORING_PAUSED
                !installed -> RuleStatus.NOT_INSTALLED
                !app.enabled -> RuleStatus.PAUSED_BY_APP
                group?.enabled == false -> RuleStatus.PAUSED_BY_GROUP
                inherited && rule.signature() in overridingSignatures -> RuleStatus.OVERRIDDEN
                !permissions.usage -> RuleStatus.NEEDS_PERMISSION
                rule.alertType == AlertType.OVERLAY && !permissions.overlay -> RuleStatus.NEEDS_PERMISSION
                rule.alertType == AlertType.NOTIFICATION && !permissions.notifications -> RuleStatus.NEEDS_PERMISSION
                else -> RuleStatus.ACTIVE
            }
            ResolvedRule(rule, group?.name.takeIf { inherited }, status)
        }
    }
}

data class RuleValidationError(val field: String, val message: String)

object RuleValidator {
    fun matchingRule(rule: Rule, rules: List<Rule>): Rule? = rules.firstOrNull {
        it.id != rule.id && it.ownerType == rule.ownerType && it.ownerId == rule.ownerId &&
            it.signature() == rule.signature()
    }

    fun validate(rule: Rule, rules: List<Rule> = emptyList()): List<RuleValidationError> = buildList {
        if (rule.ownerId.isBlank()) add(RuleValidationError("ownerId", "Chọn ứng dụng hoặc nhóm cho quy tắc."))
        if (rule.triggerType != TriggerType.ON_LAUNCH && (rule.thresholdMinutes ?: 0) < 1) {
            add(RuleValidationError("thresholdMinutes", "Nhập thời gian từ 1 phút trở lên."))
        }
        if (rule.triggerType == TriggerType.CONTINUOUS_USE && rule.repeatEnabled) {
            if ((rule.repeatEveryMinutes ?: 0) < 1) {
                add(RuleValidationError("repeatEveryMinutes", "Khoảng lặp phải từ 1 phút trở lên."))
            }
            if ((rule.repeatMaxCount ?: 0) < 1) {
                add(RuleValidationError("repeatMaxCount", "Số lần lặp phải từ 1 trở lên."))
            }
        }
        if (matchingRule(rule, rules) != null) {
            add(RuleValidationError("duplicate", "Đã có quy tắc trùng khớp. Hãy sửa quy tắc hiện có."))
        }
    }
}

fun Rule.normalized(): Rule = when (triggerType) {
    TriggerType.ON_LAUNCH -> copy(thresholdMinutes = null, repeatEnabled = false,
        repeatEveryMinutes = null, repeatMaxCount = null, customMessage = customMessage.trim())
    TriggerType.DAILY_TOTAL -> copy(repeatEnabled = false, repeatEveryMinutes = null,
        repeatMaxCount = null, customMessage = customMessage.trim())
    TriggerType.CONTINUOUS_USE -> if (repeatEnabled) copy(customMessage = customMessage.trim())
        else copy(repeatEveryMinutes = null, repeatMaxCount = null, customMessage = customMessage.trim())
}
