package com.kaoyan.timer.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

class TimeUtilTest {
    private fun localMillis(
        timeZone: TimeZone,
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int = 0
    ): Long = GregorianCalendar(timeZone, Locale.US).run {
        clear()
        set(year, month - 1, day, hour, minute, 0)
        timeInMillis
    }

    @Test
    fun crossMidnightIntervalIsSplitIntoItsLocalDates() {
        val zone = TimeZone.getTimeZone("Asia/Shanghai")
        val start = localMillis(zone, 2026, 7, 9, 23)
        val end = localMillis(zone, 2026, 7, 10, 0, 30)

        val slices = TimeUtil.splitByLocalDay(start, end, zone)

        assertEquals(listOf("2026-07-09", "2026-07-10"), slices.map { it.dayKey })
        assertEquals(listOf(3600.0, 1800.0), slices.map { it.seconds })
        assertEquals((end - start) / 1000.0, slices.sumOf { it.seconds }, 0.0)
    }

    @Test
    fun springDstDayUsesCalendarMidnightsInsteadOfFixed24Hours() {
        val zone = TimeZone.getTimeZone("America/New_York")
        val start = localMillis(zone, 2024, 3, 9, 23)
        val end = localMillis(zone, 2024, 3, 11, 1)

        val slices = TimeUtil.splitByLocalDay(start, end, zone)

        assertEquals(listOf("2024-03-09", "2024-03-10", "2024-03-11"), slices.map { it.dayKey })
        assertEquals(listOf(3600.0, 23 * 3600.0, 3600.0), slices.map { it.seconds })
        assertEquals(23 * 3600.0, TimeUtil.secondsOnLocalDay(start, end, "2024-03-10", zone), 0.0)
    }

    @Test
    fun dayOffsetRemainsCalendarCorrectAcrossDstChange() {
        val zone = TimeZone.getTimeZone("America/New_York")
        val now = localMillis(zone, 2024, 3, 11, 0, 30)

        assertEquals("2024-03-10", TimeUtil.dayKeyOffset(now, 1, zone))
    }

    @Test
    fun persistedKeysStayAsciiGregorianUnderThaiLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("th-TH"))
            val zone = TimeZone.getTimeZone("Asia/Shanghai")
            val instant = localMillis(zone, 2026, 7, 10, 12)
            assertEquals("2026-07-10", TimeUtil.todayKey(instant, zone))
            assertEquals("2026-07-10", TimeUtil.canonicalDayKey("๒๕๖๙-๐๗-๑๐", zone))
        } finally {
            Locale.setDefault(previous)
        }
    }
}
