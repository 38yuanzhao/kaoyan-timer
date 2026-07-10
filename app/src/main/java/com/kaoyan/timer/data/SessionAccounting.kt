package com.kaoyan.timer.data

import com.kaoyan.timer.model.AppState
import com.kaoyan.timer.model.Session
import com.kaoyan.timer.model.SessionLedger
import com.kaoyan.timer.model.Subject
import com.kaoyan.timer.util.TimeUtil
import com.kaoyan.timer.util.applyNonNegativeDelta

private const val ITEM_KEY_SEPARATOR = "\u001f"

data class RunningInterval(val itemId: String, val startAt: Long, val endAt: Long)

fun runningIntervalsExcept(subjects: List<Subject>, selectedItemId: String, now: Long): List<RunningInterval> =
    subjects.flatMap { subject ->
        subject.items.mapNotNull { item ->
            val start = item.runningSince
            if (item.id == selectedItemId || start == null) null else RunningInterval(item.id, start, now)
        }
    }

fun copySessionLedger(ledger: SessionLedger?): SessionLedger? = ledger?.copy(
    itemBase = ledger.itemBase.toMutableMap(),
    dailyBase = ledger.dailyBase.toMutableMap(),
    dailySubBase = ledger.dailySubBase.mapValues { it.value.toMutableMap() }.toMutableMap()
)

fun sessionDayKey(session: Session): String =
    session.dayKey.ifBlank { TimeUtil.todayKey(session.endAt) }

private fun itemKey(generationId: String, itemId: String): String =
    generationId + ITEM_KEY_SEPARATOR + itemId

private fun Session.requestedItemDelta(): Double =
    requestedDeltaSecs ?: itemDeltaSecs ?: secs

private fun Session.requestedDailyDelta(): Double =
    requestedDeltaSecs ?: dailyDeltaSecs ?: secs

private fun Session.requestedSubjectDelta(): Double =
    requestedDeltaSecs ?: subjectDeltaSecs ?: secs

private fun Session.actualItemDelta(): Double = itemDeltaSecs ?: secs
private fun Session.actualDailyDelta(): Double = dailyDeltaSecs ?: secs
private fun Session.actualSubjectDelta(): Double = subjectDeltaSecs ?: secs

private fun currentItemMatches(state: AppState, session: Session): Boolean {
    if (session.itemGenerationId.isBlank() || session.itemGenerationId != state.itemGenerationId) return false
    val subject = state.subjects.firstOrNull { candidate ->
        candidate.items.any { it.id == session.itemId }
    } ?: return false
    val item = subject.items.firstOrNull { it.id == session.itemId } ?: return false
    return item.name == session.itemName && subject.name == session.subject
}

/**
 * One-time migration from materialized aggregates. Actual deltas telescope back to the
 * checkpoint exactly for new records. Legacy records without actual-delta metadata use
 * secs as a best-effort fallback; blank legacy generations are deliberately not attached
 * to the current template's item totals.
 */
fun ensureSessionLedger(state: AppState) {
    if (state.sessionLedger != null) return
    val itemBase = mutableMapOf<String, Double>()
    for (subject in state.subjects) for (item in subject.items) {
        itemBase[itemKey(state.itemGenerationId, item.id)] = item.seconds
    }
    val dailyBase = state.daily.toMutableMap()
    val dailySubBase = state.dailySub.mapValues { it.value.toMutableMap() }.toMutableMap()

    for (session in state.sessions) {
        val dayKey = sessionDayKey(session)
        dailyBase[dayKey] = (dailyBase[dayKey] ?: 0.0) - session.actualDailyDelta()
        if (session.subject.isNotEmpty()) {
            val bySubject = dailySubBase.getOrPut(dayKey) { mutableMapOf() }
            bySubject[session.subject] =
                (bySubject[session.subject] ?: 0.0) - session.actualSubjectDelta()
        }
        if (currentItemMatches(state, session)) {
            val key = itemKey(session.itemGenerationId, session.itemId)
            itemBase[key] = (itemBase[key] ?: 0.0) - session.actualItemDelta()
        }
    }
    itemBase.replaceAll { _, value -> value.coerceAtLeast(0.0) }
    dailyBase.replaceAll { _, value -> value.coerceAtLeast(0.0) }
    for (bySubject in dailySubBase.values) {
        bySubject.replaceAll { _, value -> value.coerceAtLeast(0.0) }
    }
    state.sessionLedger = SessionLedger(itemBase, dailyBase, dailySubBase)
    rebuildSessionAggregates(state)
}

private fun apply(map: MutableMap<String, Double>, key: String, delta: Double) {
    map[key] = applyNonNegativeDelta(map[key] ?: 0.0, delta).value
}

private fun applyToLedgerBase(ledger: SessionLedger, session: Session) {
    val dayKey = sessionDayKey(session)
    apply(ledger.dailyBase, dayKey, session.requestedDailyDelta())
    if (session.subject.isNotEmpty()) {
        val bySubject = ledger.dailySubBase.getOrPut(dayKey) { mutableMapOf() }
        apply(bySubject, session.subject, session.requestedSubjectDelta())
    }
    if (session.itemGenerationId.isNotBlank()) {
        apply(
            ledger.itemBase,
            itemKey(session.itemGenerationId, session.itemId),
            session.requestedItemDelta()
        )
    }
}

/** Rebuild materialized non-negative aggregates by replaying retained events oldest first. */
fun rebuildSessionAggregates(state: AppState) {
    val ledger = state.sessionLedger ?: return
    state.daily = ledger.dailyBase.toMutableMap()
    state.dailySub = ledger.dailySubBase.mapValues { it.value.toMutableMap() }.toMutableMap()
    for (subject in state.subjects) for (item in subject.items) {
        item.seconds = ledger.itemBase[itemKey(state.itemGenerationId, item.id)] ?: 0.0
    }

    for (session in state.sessions.asReversed()) {
        val dayKey = sessionDayKey(session)
        apply(state.daily, dayKey, session.requestedDailyDelta())
        if (session.subject.isNotEmpty()) {
            val bySubject = state.dailySub.getOrPut(dayKey) { mutableMapOf() }
            apply(bySubject, session.subject, session.requestedSubjectDelta())
        }
        if (currentItemMatches(state, session)) {
            val item = state.subjects.asSequence()
                .flatMap { it.items.asSequence() }
                .first { it.id == session.itemId }
            item.seconds = applyNonNegativeDelta(item.seconds, session.requestedItemDelta()).value
        }
    }
}

/** Add a newest-first event, checkpoint any evicted oldest event, then replay. */
fun recordSessionAndReplay(state: AppState, session: Session, maxSessions: Int) {
    ensureSessionLedger(state)
    state.sessions.add(0, session)
    val ledger = state.sessionLedger ?: return
    while (state.sessions.size > maxSessions) {
        applyToLedgerBase(ledger, state.sessions.removeAt(state.sessions.lastIndex))
    }
    rebuildSessionAggregates(state)
}

fun removeSessionAndReplay(state: AppState, sessionId: String): Session? {
    ensureSessionLedger(state)
    val index = state.sessions.indexOfFirst { it.id == sessionId }
    if (index < 0) return null
    val removed = state.sessions.removeAt(index)
    rebuildSessionAggregates(state)
    return removed
}
