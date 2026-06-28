package com.kaoyan.timer.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TimeUtil {

    const val DAY_MS: Long = 86_400_000L
    const val HOUR_MS: Long = 3_600_000L

    private val sdf: SimpleDateFormat
        get() = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    fun todayKey(millis: Long): String {
        return sdf.format(Date(millis))
    }

    fun dayKeyOffset(now: Long, daysAgo: Int): String {
        return todayKey(now - daysAgo.toLong() * DAY_MS)
    }

    fun dateToMillisLocalMidnight(yyyyMMdd: String): Long {
        return try {
            sdf.parse(yyyyMMdd)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
}

/**
 * 顶层格式化函数,UI 通过 import com.kaoyan.timer.util.fmt / mmss 调用。
 */
fun fmt(seconds: Double): String {
    val total = seconds.toLong()
    val h = total / 3600
    val m = (total % 3600) / 60
    return "${h}h ${m}m"
}

fun mmss(seconds: Long): String {
    val s = if (seconds < 0) 0 else seconds
    val m = s / 60
    val sec = s % 60
    return String.format(Locale.getDefault(), "%02d:%02d", m, sec)
}

/** 分钟优先:不足 60 分钟显示「X 分钟」,满 60 分钟显示「X 小时Y 分钟」(整点省略分钟)。用于有空间的文案。 */
fun hm(seconds: Double): String {
    val m = (seconds / 60).toLong().coerceAtLeast(0)
    if (m < 60) return "${m}分钟"
    val r = m % 60
    return if (r == 0L) "${m / 60}小时" else "${m / 60}小时${r}分钟"
}

/** hm 的紧凑版(时/分),给饼图、柱状图等空间窄的地方用。 */
fun hmc(seconds: Double): String {
    val m = (seconds / 60).toLong().coerceAtLeast(0)
    if (m < 60) return "${m}分"
    val r = m % 60
    return if (r == 0L) "${m / 60}时" else "${m / 60}时${r}分"
}
