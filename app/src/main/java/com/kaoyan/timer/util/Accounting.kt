package com.kaoyan.timer.util

/** Result of applying a signed delta to a value whose persisted domain is non-negative. */
data class AppliedNonNegativeDelta(val value: Double, val delta: Double)

/** Pure accounting primitive used by manual adjustments and their rollback. */
fun applyNonNegativeDelta(current: Double, requestedDelta: Double): AppliedNonNegativeDelta {
    val safeCurrent = current.coerceAtLeast(0.0)
    if (!requestedDelta.isFinite()) return AppliedNonNegativeDelta(safeCurrent, 0.0)
    val value = (safeCurrent + requestedDelta).coerceAtLeast(0.0)
    return AppliedNonNegativeDelta(value = value, delta = value - safeCurrent)
}

fun rollbackNonNegativeDelta(current: Double, appliedDelta: Double): Double =
    (current.coerceAtLeast(0.0) - appliedDelta).coerceAtLeast(0.0)
