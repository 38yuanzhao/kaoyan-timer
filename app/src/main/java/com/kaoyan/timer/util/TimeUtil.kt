package com.kaoyan.timer.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

object TimeUtil {

    const val DAY_MS: Long = 86_400_000L
    const val HOUR_MS: Long = 3_600_000L

    data class LocalDaySlice(
        val dayKey: String,
        val startAt: Long,
        val endAt: Long
    ) {
        val seconds: Double get() = (endAt - startAt) / 1000.0
    }

    private fun dateFormat(timeZone: TimeZone = TimeZone.getDefault()) =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            calendar = GregorianCalendar(timeZone, Locale.US)
            this.timeZone = timeZone
        }

    fun todayKey(millis: Long, timeZone: TimeZone = TimeZone.getDefault()): String {
        return dateFormat(timeZone).format(Date(millis))
    }

    /** Calendar-based day offset so DST days are not assumed to contain exactly 24 hours. */
    fun dayKeyOffset(
        now: Long,
        daysAgo: Int,
        timeZone: TimeZone = TimeZone.getDefault()
    ): String {
        val cal = Calendar.getInstance(timeZone).apply {
            timeInMillis = now
            add(Calendar.DAY_OF_MONTH, -daysAgo)
        }
        return todayKey(cal.timeInMillis, timeZone)
    }

    fun dateToMillisLocalMidnight(yyyyMMdd: String): Long {
        return try {
            dateFormat().parse(yyyyMMdd)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    /** Convert recognizable legacy localized keys to the persisted Gregorian/ASCII format. */
    fun canonicalDayKey(
        raw: String,
        timeZone: TimeZone = TimeZone.getDefault()
    ): String? {
        val ascii = raw.trim().map { ch ->
            val digit = Character.digit(ch, 10)
            if (digit >= 0) ('0'.code + digit).toChar() else ch
        }.joinToString("")
        val match = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$").matchEntire(ascii) ?: return null
        var year = match.groupValues[1].toIntOrNull() ?: return null
        // Thai Buddhist-calendar keys from the old Locale.getDefault formatter.
        if (year in 2400..2999) year -= 543
        val candidate = String.format(
            Locale.US,
            "%04d-%s-%s",
            year,
            match.groupValues[2],
            match.groupValues[3]
        )
        return try {
            val parser = dateFormat(timeZone).apply { isLenient = false }
            val parsed = parser.parse(candidate) ?: return null
            parser.format(parsed)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Split [startAt, endAt) at local-midnight boundaries. Calendar arithmetic is
     * intentional: on DST transitions a local day can be 23 or 25 hours long.
     */
    fun splitByLocalDay(
        startAt: Long,
        endAt: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): List<LocalDaySlice> {
        if (endAt <= startAt) return emptyList()
        val result = mutableListOf<LocalDaySlice>()
        var cursor = startAt
        while (cursor < endAt) {
            val nextMidnight = Calendar.getInstance(timeZone).run {
                timeInMillis = cursor
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                add(Calendar.DAY_OF_MONTH, 1)
                timeInMillis
            }
            // Defensive fallback for a pathological time-zone transition.
            if (nextMidnight <= cursor) break
            val sliceEnd = minOf(endAt, nextMidnight)
            result += LocalDaySlice(
                dayKey = todayKey(cursor, timeZone),
                startAt = cursor,
                endAt = sliceEnd
            )
            cursor = sliceEnd
        }
        return result
    }

    fun secondsOnLocalDay(
        startAt: Long,
        endAt: Long,
        dayKey: String,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Double {
        if (endAt <= startAt) return 0.0
        val dayStart = try {
            dateFormat(timeZone).apply { isLenient = false }.parse(dayKey)?.time ?: return 0.0
        } catch (_: Exception) {
            return 0.0
        }
        val dayEnd = Calendar.getInstance(timeZone).run {
            timeInMillis = dayStart
            add(Calendar.DAY_OF_MONTH, 1)
            timeInMillis
        }
        val overlapStart = maxOf(startAt, dayStart)
        val overlapEnd = minOf(endAt, dayEnd)
        return (overlapEnd - overlapStart).coerceAtLeast(0L) / 1000.0
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
