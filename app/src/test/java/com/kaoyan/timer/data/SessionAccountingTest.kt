package com.kaoyan.timer.data

import com.kaoyan.timer.model.AppState
import com.kaoyan.timer.model.Item
import com.kaoyan.timer.model.Session
import com.kaoyan.timer.model.Subject
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionAccountingTest {
    private fun state(generation: String = "g") = AppState(
        subjects = listOf(Subject("数学", listOf(Item("0", "高数")))),
        itemGenerationId = generation
    ).also(::ensureSessionLedger)

    private fun session(id: String, delta: Double, generation: String = "g") = Session(
        id = id, startAt = 1, endAt = 2, secs = delta,
        subject = "数学", itemId = "0", itemName = "高数", kind = "manual",
        dayKey = "2026-07-10", requestedDeltaSecs = delta,
        itemGenerationId = generation
    )

    @Test
    fun deletingPositiveBeforeNegativeDoesNotCreatePhantomTime() {
        val state = state()
        recordSessionAndReplay(state, session("plus", 600.0), 500)
        recordSessionAndReplay(state, session("minus", -600.0), 500)

        removeSessionAndReplay(state, "plus")
        assertEquals(0.0, state.subjects[0].items[0].seconds, 0.0)
        removeSessionAndReplay(state, "minus")

        assertEquals(0.0, state.subjects[0].items[0].seconds, 0.0)
        assertEquals(0.0, state.daily["2026-07-10"] ?: 0.0, 0.0)
    }

    @Test
    fun evictedOldestEventIsFoldedIntoCheckpoint() {
        val state = state()
        recordSessionAndReplay(state, session("one", 300.0), 2)
        recordSessionAndReplay(state, session("two", 300.0), 2)
        recordSessionAndReplay(state, session("minus", -600.0), 2)
        removeSessionAndReplay(state, "two")
        removeSessionAndReplay(state, "minus")

        assertEquals(300.0, state.subjects[0].items[0].seconds, 0.0)
    }

    @Test
    fun oldGenerationNeverMutatesCurrentTemplateItem() {
        val state = state("new")
        recordSessionAndReplay(state, session("old", 600.0, "old"), 500)

        assertEquals(0.0, state.subjects[0].items[0].seconds, 0.0)
        removeSessionAndReplay(state, "old")
        assertEquals(0.0, state.subjects[0].items[0].seconds, 0.0)
    }

    @Test
    fun reportsEveryOtherRunningStopwatchForExclusiveSettlement() {
        val subjects = listOf(Subject("数学", listOf(
            Item("a", "高数", runningSince = 10),
            Item("b", "线代", runningSince = 20),
            Item("c", "概率")
        )))

        assertEquals(listOf("a"), runningIntervalsExcept(subjects, "b", 30).map { it.itemId })
    }
}
