package com.kaoyan.timer.ui

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Material3 DatePicker 对话框。selectedMillis 为 UTC 毫秒(DatePicker 约定 UTC 0 点)。
 * 回调返回 "yyyy-MM-dd"。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KaoyanDatePickerDialog(
    initialDate: String,
    latestDate: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val initMillis = parseDateToUtcMillis(initialDate)
    val selectableDates = remember(latestDate) {
        val latestMillis = latestDate?.let(::parseDateToUtcMillis)
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                latestMillis == null || utcTimeMillis <= latestMillis
        }
    }
    val dpState = rememberDatePickerState(
        initialSelectedDateMillis = initMillis,
        selectableDates = selectableDates
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val ms = dpState.selectedDateMillis
                if (ms != null) onConfirm(utcMillisToDateStr(ms))
                else onDismiss()
            }) { Text("确定", color = ColorGood) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = ColorMuted) }
        },
        colors = DatePickerDefaults.colors(
            containerColor = ColorCard2
        )
    ) {
        DatePicker(
            state = dpState,
            colors = DatePickerDefaults.colors(
                containerColor = ColorCard2,
                titleContentColor = ColorFg,
                headlineContentColor = ColorFg,
                weekdayContentColor = ColorMuted,
                dayContentColor = ColorFg,
                selectedDayContainerColor = ColorGood,
                selectedDayContentColor = ColorBg,
                todayContentColor = ColorAccent2,
                todayDateBorderColor = ColorAccent2
            )
        )
    }
}

private fun parseDateToUtcMillis(yyyyMMdd: String): Long? {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        sdf.parse(yyyyMMdd)?.time
    } catch (e: Exception) {
        null
    }
}

private fun utcMillisToDateStr(millis: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    sdf.timeZone = TimeZone.getTimeZone("UTC")
    return sdf.format(java.util.Date(millis))
}
