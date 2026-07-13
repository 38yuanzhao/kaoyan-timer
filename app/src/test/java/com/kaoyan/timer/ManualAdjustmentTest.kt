package com.kaoyan.timer

import com.kaoyan.timer.util.TimeUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ManualAdjustmentTest {
    @Test
    fun selectedDateOverridesTodayButFutureDatesAreRejected() {
        val now = TimeUtil.dateToMillisLocalMidnight("2026-07-11") + TimeUtil.HOUR_MS

        assertEquals("2026-07-10", resolveManualDayKey("2026-07-10", now))
        assertEquals("2026-07-11", resolveManualDayKey(null, now))
        assertNull(resolveManualDayKey("2026-07-12", now))
        assertNull(resolveManualDayKey("not-a-date", now))
    }
}
