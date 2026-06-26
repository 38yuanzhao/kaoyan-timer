package com.kaoyan.timer.util

import java.text.SimpleDateFormat
import java.util.Calendar
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
            val d = sdf.parse(yyyyMMdd) ?: return 0L
            val cal = Calendar.getInstance()
            cal.time = d
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.timeInMillis
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
