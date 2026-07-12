package com.kaoyan.timer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaoyan.timer.R
import com.kaoyan.timer.model.Session
import com.kaoyan.timer.util.TimeUtil
import com.kaoyan.timer.util.hmc
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.roundToLong

private const val COLLAPSED_COUNT = 8
private val ExpandedListMaxHeight = 480.dp

/**
 * Stable, defensive snapshot used to keep the record card out of the screen's one-second timer
 * recomposition when the records themselves have not changed.
 */
@Immutable
class SessionLogSnapshot private constructor(val sessions: List<Session>) {
    companion object {
        fun from(sessions: List<Session>): SessionLogSnapshot = SessionLogSnapshot(sessions.toList())
    }
}

@Immutable
private data class SessionRowUi(
    val session: Session,
    val dayKey: String,
    val title: String,
    val timeText: String,
    val durationMagnitude: String,
    val durationText: String,
    val isDeduction: Boolean
)

private fun kindLabel(kind: String): String = when (kind) {
    "pomo" -> "番茄"
    "chain" -> "拆解链"
    "manual" -> "手动"
    else -> "秒表"
}

/** 保留不足一分钟的真实秒数，避免负记录显示成误导性的“−0分”。 */
private fun compactDuration(seconds: Double): String {
    val magnitude = abs(seconds)
    if (magnitude > 0.0 && magnitude < 60.0) {
        return "${magnitude.roundToLong().coerceAtLeast(1L)}秒"
    }
    return hmc(magnitude)
}

private fun dayLabel(dayKey: String, todayKey: String, yesterdayKey: String): String = when (dayKey) {
    todayKey -> "今天"
    yesterdayKey -> "昨天"
    else -> dayKey
}

/** 学习记录明细：按天分组倒序展示每次结算，可删除误计时并回滚统计。 */
@Composable
fun SessionLogCard(
    snapshot: SessionLogSnapshot,
    todayKey: String,
    yesterdayKey: String,
    onDeleteSession: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<SessionRowUi?>(null) }

    val locale = Locale.getDefault()
    val timeZone = TimeZone.getDefault()
    val rows = remember(snapshot, locale.toLanguageTag(), timeZone.id) {
        val hhmm = SimpleDateFormat("HH:mm", locale).apply { this.timeZone = timeZone }
        snapshot.sessions.map { session ->
            val isDeduction = session.secs < 0.0
            val magnitude = compactDuration(session.secs)
            val timeText = if (session.kind == "manual") {
                val action = if (isDeduction) "手动扣减" else "手动增加"
                "${hhmm.format(Date(session.endAt))} $action"
            } else {
                "${hhmm.format(Date(session.startAt))} – ${hhmm.format(Date(session.endAt))} · ${kindLabel(session.kind)}"
            }
            SessionRowUi(
                session = session,
                dayKey = session.dayKey.ifBlank { TimeUtil.todayKey(session.endAt) },
                title = listOf(session.subject, session.itemName)
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                    .ifBlank { "未命名记录" },
                timeText = timeText,
                durationMagnitude = magnitude,
                durationText = if (isDeduction) "−$magnitude" else magnitude,
                isDeduction = isDeduction
            )
        }
    }

    SectionCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("学习记录", color = ColorFg, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text("最近 ${rows.size} 条", color = ColorMuted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(6.dp))

        if (rows.isEmpty()) {
            Text("还没有记录，开始一次专注或秒表计时吧。", color = ColorMuted, fontSize = 13.sp)
        } else if (expanded) {
            // 父页面是可滚动 Column；有限高度为 LazyColumn 提供约束，并确保最多只组合可见记录。
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = ExpandedListMaxHeight)
            ) {
                itemsIndexed(
                    items = rows,
                    key = { _, row -> row.session.id },
                    contentType = { index, row ->
                        if (index == 0 || rows[index - 1].dayKey != row.dayKey) "with_day" else "session"
                    }
                ) { index, row ->
                    SessionListEntry(
                        row = row,
                        showDayHeader = index == 0 || rows[index - 1].dayKey != row.dayKey,
                        dayLabel = dayLabel(row.dayKey, todayKey, yesterdayKey),
                        onDelete = { pendingDelete = row }
                    )
                }
            }
        } else {
            // 收起时只有八条，直接随父页面滚动，避免小列表出现嵌套滚动手势。
            rows.take(COLLAPSED_COUNT).forEachIndexed { index, row ->
                SessionListEntry(
                    row = row,
                    showDayHeader = index == 0 || rows[index - 1].dayKey != row.dayKey,
                    dayLabel = dayLabel(row.dayKey, todayKey, yesterdayKey),
                    onDelete = { pendingDelete = row }
                )
            }
        }

        if (rows.size > COLLAPSED_COUNT) {
            Spacer(Modifier.height(8.dp))
            Text(
                if (expanded) "收起" else "展开全部 ${rows.size} 条",
                color = ColorAccent2,
                fontSize = 13.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(vertical = 4.dp)
            )
        }
    }

    pendingDelete?.let { row ->
        ConfirmDialog(
            title = stringResource(R.string.session_delete_title),
            message = if (row.isDeduction) {
                stringResource(
                    R.string.session_delete_deduction_message,
                    row.title,
                    row.durationMagnitude
                )
            } else {
                stringResource(
                    R.string.session_delete_positive_message,
                    row.title,
                    row.durationMagnitude
                )
            },
            confirmText = stringResource(R.string.session_delete_confirm),
            onDismiss = { pendingDelete = null },
            onConfirm = {
                onDeleteSession(row.session.id)
                pendingDelete = null
            }
        )
    }
}

@Composable
private fun SessionListEntry(
    row: SessionRowUi,
    showDayHeader: Boolean,
    dayLabel: String,
    onDelete: () -> Unit
) {
    if (showDayHeader) {
        Spacer(Modifier.height(8.dp))
        Text(
            dayLabel,
            color = ColorMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(2.dp))
    }
    SessionRow(row, onDelete)
    HorizontalDivider(color = ColorLine, thickness = 0.5.dp)
}

@Composable
private fun SessionRow(row: SessionRowUi, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                row.title,
                color = ColorFg,
                fontSize = 13.sp,
                maxLines = 1
            )
            Spacer(Modifier.height(2.dp))
            Text(row.timeText, color = ColorMuted, fontSize = 11.sp)
        }
        Text(
            row.durationText,
            color = if (row.isDeduction) ColorAccent else ColorGood,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "删",
            color = ColorMuted,
            fontSize = 12.sp,
            modifier = Modifier
                .clickable { onDelete() }
                .padding(6.dp)
        )
    }
}
