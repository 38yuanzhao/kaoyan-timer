package com.kaoyan.timer.util

import com.kaoyan.timer.model.Pomo
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class PomoAccountingTest {
    private val zone = TimeZone.getTimeZone("Asia/Shanghai")
    private fun at(day: Int, hour: Int, minute: Int = 0) = Calendar.getInstance(zone).run {
        clear(); set(2026, 6, day, hour, minute, 0); timeInMillis
    }

    @Test
    fun pauseAcrossMidnightKeepsWorkOnActualDates() {
        val start = at(9, 23)
        var pomo = Pomo("item", "focus", start, at(10, 1),
            focusSegments = emptyList(), activeFocusStartedAt = start)
        pomo = pausePomoAt(pomo, at(9, 23, 30))
        pomo = resumePomoAt(pomo, at(10, 0, 30))
        val segments = pomo.focusIntervalsUntil(at(10, 1))

        assertEquals(2 * 3600_000L, pomo.endsAt - pomo.startAt)
        assertEquals(1800.0, segments.sumOf {
            TimeUtil.secondsOnLocalDay(it.startAt, it.endAt, "2026-07-09", zone)
        }, 0.0)
        assertEquals(1800.0, segments.sumOf {
            TimeUtil.secondsOnLocalDay(it.startAt, it.endAt, "2026-07-10", zone)
        }, 0.0)
        assertEquals(3600.0, pomo.focusedSecondsUntil(at(10, 1)), 0.0)
    }
}
