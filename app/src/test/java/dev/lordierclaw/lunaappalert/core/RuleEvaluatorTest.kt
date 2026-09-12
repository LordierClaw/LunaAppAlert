package dev.lordierclaw.lunaappalert.core

import org.junit.Assert.*
import org.junit.Test

class RuleEvaluatorTest {
    private val app = TrackedApp("app", "example.app", "Ứng dụng")
    private fun rule(id: String = "rule", type: TriggerType = TriggerType.CONTINUOUS_USE, threshold: Int = 20) =
        Rule(id, OwnerType.APP, app.id, triggerType = type, thresholdMinutes = threshold)
    private fun active(vararg rules: Rule) = rules.map { ResolvedRule(it, null, RuleStatus.ACTIVE) }
    private fun context(minutes: Long = 0, session: String? = "s1", date: String = "2026-09-12", newSession: Boolean = false) =
        EvaluationContext(session, minutes * 60_000L, minutes * 60_000L, date, newSession, 1_800_000_000_000L + minutes * 60_000L)

    @Test fun launchFiresOnlyOncePerNewSession() {
        val rules = active(rule(type = TriggerType.ON_LAUNCH))
        val first = RuleEvaluator.evaluate(app, rules, context(newSession = true), EvaluationState())
        assertEquals(1, first.dueAlerts.size)
        assertTrue(RuleEvaluator.evaluate(app, rules, context(newSession = true), first.state).dueAlerts.isEmpty())
        assertEquals(1, RuleEvaluator.evaluate(app, rules, context(session = "s2", newSession = true), first.state).dueAlerts.size)
        assertTrue(RuleEvaluator.evaluate(app, rules, context(), EvaluationState()).dueAlerts.isEmpty())
    }

    @Test fun repeatCountExcludesInitialAndDoesNotRefireOnDuplicateTick() {
        val rules = active(rule().copy(repeatEnabled = true, repeatEveryMinutes = 5, repeatMaxCount = 3))
        var state = EvaluationState()
        assertTrue(RuleEvaluator.evaluate(app, rules, context(19), state).dueAlerts.isEmpty())
        listOf(20L, 25L, 30L, 35L).forEachIndexed { index, minute ->
            val result = RuleEvaluator.evaluate(app, rules, context(minute), state)
            assertEquals(index, result.dueAlerts.single().occurrence)
            state = result.state
            assertTrue(RuleEvaluator.evaluate(app, rules, context(minute), state).dueAlerts.isEmpty())
        }
        assertTrue(RuleEvaluator.evaluate(app, rules, context(40), state).dueAlerts.isEmpty())
    }

    @Test fun delayedRepeatTickCoalescesStaleOccurrences() {
        val rules = active(rule().copy(repeatEnabled = true, repeatEveryMinutes = 5, repeatMaxCount = 3))
        val result = RuleEvaluator.evaluate(app, rules, context(32), EvaluationState())
        assertEquals(2, result.dueAlerts.single().occurrence)
        assertEquals(context(30).nowMillis, result.dueAlerts.single().dueAtMillis)
        assertEquals(3, RuleEvaluator.evaluate(app, rules, context(35), result.state).dueAlerts.single().occurrence)
    }

    @Test fun differentContinuousThresholdsAreIndependent() {
        val rules = active(rule("20"), rule("45", threshold = 45))
        val at20 = RuleEvaluator.evaluate(app, rules, context(20), EvaluationState())
        assertEquals(listOf("20"), at20.dueAlerts.map { it.rule.id })
        assertEquals(listOf("45"), RuleEvaluator.evaluate(app, rules, context(45), at20.state).dueAlerts.map { it.rule.id })
    }

    @Test fun dailyTotalFiresOnceAcrossSessionsAndRuleEditsButNewRuleIsEligible() {
        val daily = rule(type = TriggerType.DAILY_TOTAL, threshold = 60)
        val fired = RuleEvaluator.evaluate(app, active(daily), context(60), EvaluationState())
        val edited = daily.copy(thresholdMinutes = 30, customMessage = "Đổi nội dung")
        assertTrue(RuleEvaluator.evaluate(app, active(edited), context(70, session = "new-session"), fired.state).dueAlerts.isEmpty())
        assertEquals(1, RuleEvaluator.evaluate(app, active(edited.copy(id = "new-rule")), context(70), fired.state).dueAlerts.size)
    }

    @Test fun groupDailyRuleTracksEachAppSeparately() {
        val daily = rule(type = TriggerType.DAILY_TOTAL, threshold = 60).copy(ownerType = OwnerType.GROUP, ownerId = "g")
        val first = RuleEvaluator.evaluate(app, active(daily), context(60), EvaluationState())
        val secondApp = app.copy(id = "second", packageName = "example.second")
        assertTrue(RuleEvaluator.evaluate(secondApp, active(daily), context(30, session = "s2"), first.state).dueAlerts.isEmpty())
        val second = RuleEvaluator.evaluate(secondApp, active(daily), context(60, session = "s2"), first.state)
        assertEquals(1, second.dueAlerts.size)
        assertEquals(2, second.state.dailyFired.size)
    }

    @Test fun localDateRolloverClearsDailyLedgerAndDoesNotResetContinuousSession() {
        val rules = active(rule("daily", TriggerType.DAILY_TOTAL), rule("continuous"))
        val first = RuleEvaluator.evaluate(app, rules, context(20), EvaluationState())
        val nextDay = RuleEvaluator.evaluate(app, rules, context(25, date = "2026-09-13"), first.state)
        assertEquals(listOf("daily"), nextDay.dueAlerts.map { it.rule.id })
        assertEquals("2026-09-13", nextDay.state.currentDate)
    }

    @Test fun permissionBlockedRulesDoNotConsumeOccurrences() {
        val blocked = ResolvedRule(rule(), null, RuleStatus.NEEDS_PERMISSION)
        val first = RuleEvaluator.evaluate(app, listOf(blocked), context(20), EvaluationState())
        assertTrue(first.dueAlerts.isEmpty())
        assertTrue(first.state.sessionOccurrences.isEmpty())
        assertEquals(1, RuleEvaluator.evaluate(app, active(blocked.rule), context(21), first.state).dueAlerts.size)
    }

    @Test fun newSessionOrProcessRestartResetsContinuousButPreservesDaily() {
        val rules = active(rule(), rule("daily", TriggerType.DAILY_TOTAL))
        val fired = RuleEvaluator.evaluate(app, rules, context(20), EvaluationState())
        val recovered = fired.state.copy(sessionId = null, sessionOccurrences = emptyMap())
        assertEquals(listOf("rule"), RuleEvaluator.evaluate(app, rules, context(20, session = "restarted"), recovered).dueAlerts.map { it.rule.id })
    }

    @Test fun noForegroundSessionMeansNoAlertsAndClearsSessionLedger() {
        val fired = RuleEvaluator.evaluate(app, active(rule()), context(20), EvaluationState())
        val inactive = RuleEvaluator.evaluate(app, active(rule(), rule("daily", TriggerType.DAILY_TOTAL)), context(60, session = null), fired.state)
        assertTrue(inactive.dueAlerts.isEmpty())
        assertTrue(inactive.state.sessionOccurrences.isEmpty())
    }

    @Test fun simultaneousSignaturesRemainIndependentAndAppOwnedSortsFirst() {
        val group = rule("group").copy(ownerType = OwnerType.GROUP, ownerId = "g")
        val own = rule("app", TriggerType.DAILY_TOTAL)
        val alerts = RuleEvaluator.evaluate(app, active(group, own), context(20), EvaluationState()).dueAlerts
        assertEquals(listOf("app", "group"), alerts.map { it.rule.id })
    }

    @Test fun deletingAndUndoingDailyRuleCannotFireItTwiceToday() {
        val rules = listOf(rule("keep", TriggerType.DAILY_TOTAL), rule("delete", TriggerType.DAILY_TOTAL))
        val fired = RuleEvaluator.evaluate(app, active(*rules.toTypedArray()), context(20), EvaluationState())
        val pruned = RuleEvaluator.prune(fired.state, Configuration(apps = listOf(app), rules = listOf(rules.first())))
        assertTrue(RuleEvaluator.evaluate(app, active(rules.first()), context(20), pruned).dueAlerts.isEmpty())
        assertTrue("Undo restores the same rule identity, so today's warning stays consumed",
            RuleEvaluator.evaluate(app, active(rules.last()), context(30), pruned).dueAlerts.isEmpty())
    }

    @Test fun largeMinuteValuesUseLongArithmetic() {
        val rules = active(rule(threshold = Int.MAX_VALUE))
        assertTrue(RuleEvaluator.evaluate(app, rules, context(100), EvaluationState()).dueAlerts.isEmpty())
        assertEquals(1, RuleEvaluator.evaluate(app, rules, context(Int.MAX_VALUE.toLong()), EvaluationState()).dueAlerts.size)
    }

    @Test fun movingAwayAndBackDoesNotRearmDailyGroupRule() {
        val daily = rule(type = TriggerType.DAILY_TOTAL).copy(ownerType = OwnerType.GROUP, ownerId = "group")
        val fired = RuleEvaluator.evaluate(app, active(daily), context(20), EvaluationState())
        val prunedWhileUngrouped = RuleEvaluator.prune(fired.state,
            Configuration(groups = listOf(AppGroup("group", "Nhóm")), apps = listOf(app.copy(groupId = null)), rules = listOf(daily)))
        assertTrue(RuleEvaluator.evaluate(app.copy(groupId = "group"), active(daily), context(30), prunedWhileUngrouped).dueAlerts.isEmpty())
    }
}
