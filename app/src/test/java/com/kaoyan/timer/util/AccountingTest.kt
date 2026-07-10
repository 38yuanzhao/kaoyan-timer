package com.kaoyan.timer.util

import org.junit.Assert.assertEquals
import org.junit.Test

class AccountingTest {
    @Test
    fun oversizedNegativeAdjustmentRecordsOnlyAppliedDeltaAndRollsBackExactly() {
        val applied = applyNonNegativeDelta(current = 5 * 60.0, requestedDelta = -15 * 60.0)

        assertEquals(0.0, applied.value, 0.0)
        assertEquals(-5 * 60.0, applied.delta, 0.0)
        assertEquals(5 * 60.0, rollbackNonNegativeDelta(applied.value, applied.delta), 0.0)
    }

    @Test
    fun positiveAdjustmentAndRollbackAreSymmetric() {
        val applied = applyNonNegativeDelta(current = 120.0, requestedDelta = 90.0)

        assertEquals(210.0, applied.value, 0.0)
        assertEquals(90.0, applied.delta, 0.0)
        assertEquals(120.0, rollbackNonNegativeDelta(applied.value, applied.delta), 0.0)
    }
}
