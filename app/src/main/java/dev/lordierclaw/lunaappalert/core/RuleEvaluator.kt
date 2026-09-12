package dev.lordierclaw.lunaappalert.core

data class EvaluationContext(
    val sessionId: String?,
    val continuousElapsedMillis: Long,
    val dailyUsageMillis: Long,
    val localDate: String,
    val newSession: Boolean = false,
    val nowMillis: Long = 0L,
)

/** Only the current calendar day and current foreground session are retained. */
data class EvaluationState(
    val currentDate: String = "",
    val dailyFired: Map<String, Boolean> = emptyMap(),
    val sessionId: String? = null,
    val sessionOccurrences: Map<String, Int> = emptyMap(),
)

data class DueAlert(
    val rule: Rule,
    val appId: String,
    val packageName: String,
    /** Zero is the initial alert; 1..N are additional continuous-use repeats. */
    val occurrence: Int,
    val dueAtMillis: Long,
)

data class EvaluationResult(val dueAlerts: List<DueAlert>, val state: EvaluationState)

object RuleEvaluator {
    fun evaluate(
        app: TrackedApp,
        resolvedRules: List<ResolvedRule>,
        context: EvaluationContext,
        state: EvaluationState,
    ): EvaluationResult {
        val daily = if (state.currentDate == context.localDate) state.dailyFired.toMutableMap()
            else mutableMapOf()
        val occurrences = if (state.sessionId == context.sessionId && context.sessionId != null)
            state.sessionOccurrences.toMutableMap() else mutableMapOf()
        val alerts = mutableListOf<DueAlert>()
        if (context.sessionId != null) {
            for (resolved in resolvedRules.filter { it.status == RuleStatus.ACTIVE }) {
                val rule = resolved.rule
                val key = occurrenceKey(app.id, rule.id)
                fun emit(occurrence: Int, dueAtMillis: Long = context.nowMillis) {
                    alerts += DueAlert(rule, app.id, app.packageName, occurrence, dueAtMillis)
                }
                when (rule.triggerType) {
                    TriggerType.ON_LAUNCH -> if (context.newSession && key !in occurrences) {
                        emit(0)
                        occurrences[key] = 0
                    }
                    TriggerType.DAILY_TOTAL -> {
                        val threshold = rule.thresholdMinutes?.takeIf { it > 0 }?.toLong()?.times(MINUTE)
                        if (threshold != null && context.dailyUsageMillis >= threshold && daily[key] != true) {
                            emit(0)
                            daily[key] = true
                        }
                    }
                    TriggerType.CONTINUOUS_USE -> {
                        val threshold = rule.thresholdMinutes?.takeIf { it > 0 }?.toLong()?.times(MINUTE)
                            ?: continue
                        val elapsed = context.continuousElapsedMillis.coerceAtLeast(0)
                        if (elapsed < threshold) continue
                        val interval = rule.repeatEveryMinutes?.takeIf { it > 0 }?.toLong()?.times(MINUTE)
                        val repeatCount = rule.repeatMaxCount?.takeIf { it > 0 } ?: 0
                        val dueOccurrence = if (rule.repeatEnabled && interval != null)
                            minOf((elapsed - threshold) / interval, repeatCount.toLong()).toInt() else 0
                        val previous = occurrences[key] ?: -1
                        if (dueOccurrence > previous) {
                            // A delayed observer catches up to its latest occurrence instead of
                            // displaying a burst of stale repeats. Regular ticks deliver every repeat.
                            val dueElapsed = threshold + (interval ?: 0L) * dueOccurrence.toLong()
                            val lag = (elapsed - dueElapsed).coerceAtLeast(0)
                            emit(dueOccurrence, context.nowMillis - lag)
                            occurrences[key] = dueOccurrence
                        }
                    }
                }
            }
        }
        return EvaluationResult(
            dueAlerts = alerts.sortedWith(compareBy<DueAlert> { it.dueAtMillis }
                .thenBy { if (it.rule.ownerType == OwnerType.APP) 0 else 1 }
                .thenBy { it.rule.id }),
            state = EvaluationState(context.localDate, daily, context.sessionId, occurrences),
        )
    }

    /** Discard invalid session occurrences after configuration changes. Keep today's
     * consumed daily identities until rollover so delete + Undo cannot re-arm them. */
    fun prune(state: EvaluationState, configuration: Configuration): EvaluationState {
        val appIds = configuration.apps.map { it.id }.toSet()
        val ruleIds = configuration.rules.map { it.id }.toSet()
        fun isCurrentKey(key: String): Boolean {
            val separator = key.indexOf(':')
            if (separator < 1) return false
            val length = key.substring(0, separator).toIntOrNull() ?: return false
            val start = separator + 1
            if (length < 0 || length > key.length - start) return false
            return key.substring(start, start + length) in appIds && key.substring(start + length) in ruleIds
        }
        return state.copy(
            sessionOccurrences = state.sessionOccurrences.filterKeys { isCurrentKey(it) },
        )
    }

    private const val MINUTE = 60_000L

    // A length prefix keeps identifiers unambiguous without imposing an ID alphabet.
    private fun occurrenceKey(appId: String, ruleId: String): String = "${appId.length}:$appId$ruleId"
}
