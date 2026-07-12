package com.kaoyan.timer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaoyan.timer.KaoyanViewModel
import com.kaoyan.timer.model.AppState
import com.kaoyan.timer.util.TimeUtil
import com.kaoyan.timer.util.hm
import java.util.Calendar
import java.util.Locale

private val WeekdayLabels = listOf("一", "二", "三", "四", "五", "六", "日")
private val HeatLegendSeconds = listOf(0.0, 1800.0, 2.0 * 3600, 4.0 * 3600, 7.0 * 3600)
private val HeatCellShape = RoundedCornerShape(6.dp)

@Immutable
private data class HeatmapDay(val number: Int, val key: String)

@Immutable
private data class HeatmapMonth(
    val year: Int,
    val month: Int,
    val leadingBlanks: Int,
    val days: List<HeatmapDay>
)

private fun buildHeatmapMonth(referenceMillis: Long, monthOffset: Int): HeatmapMonth {
    val calendar = Calendar.getInstance().apply {
        timeInMillis = referenceMillis
        set(Calendar.DAY_OF_MONTH, 1)
        add(Calendar.MONTH, monthOffset)
    }
    val year = calendar.get(Calendar.YEAR)
    val month = calendar.get(Calendar.MONTH)
    val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    return HeatmapMonth(
        year = year,
        month = month,
        leadingBlanks = (calendar.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7,
        days = (1..daysInMonth).map { day ->
            HeatmapDay(day, String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day))
        }
    )
}

/** 时长 → 热力等级色。0 无记录;>0 按 1/3/6 小时分四档蓝色深浅。 */
private fun heatColor(secs: Double): Color = when {
    secs <= 0.0 -> ColorCard2
    secs < 1 * 3600 -> ColorGood.copy(alpha = 0.25f)
    secs < 3 * 3600 -> ColorGood.copy(alpha = 0.45f)
    secs < 6 * 3600 -> ColorGood.copy(alpha = 0.70f)
    else -> ColorGood
}

/**
 * 学习热力图(打卡日历):按月展示每天投入的深浅,‹ › 翻月,点某天在卡片底部看
 * 当天总时长与各科分布(取自 daily / dailySub,今日叠加在途)。
 */
@Composable
fun HeatmapCard(vm: KaoyanViewModel, state: AppState, now: Long, modifier: Modifier = Modifier) {
    var monthOffset by remember { mutableIntStateOf(0) } // 0=本月,-1=上月…
    var selectedKey by remember { mutableStateOf<String?>(null) }

    val todayKey = TimeUtil.todayKey(now)
    // 月份布局和日期键只在跨日或翻月时变化，不随全局秒级时钟重复构造。
    val displayedMonth = remember(todayKey, monthOffset) { buildHeatmapMonth(now, monthOffset) }
    val hasLiveTiming = remember(state.subjects, state.pomo) {
        state.subjects.any { subject -> subject.items.any { it.runningSince != null } } ||
            state.pomo?.let { pomo ->
                pomo.pausedAt == null && (pomo.phase == "focus" || pomo.phase == "overtime")
            } == true
    }
    val liveHeatmapTick = if (hasLiveTiming) now else 0L
    val secondsByDay = remember(
        displayedMonth,
        state.daily,
        state.subjects,
        state.pomo,
        liveHeatmapTick
    ) {
        displayedMonth.days.associate { day -> day.key to vm.daySeconds(day.key, now) }
    }
    fun secsOf(key: String): Double = secondsByDay[key] ?: 0.0

    val checkedDays = displayedMonth.days.count { secsOf(it.key) > 0.0 }

    SectionCard(modifier = modifier) {
        // 标题行:月份切换 + 本月打卡数
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("学习热力图", color = ColorFg, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "‹",
                    color = ColorGood,
                    fontSize = 22.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { monthOffset--; selectedKey = null }
                        .padding(horizontal = 10.dp)
                )
                Text(
                    "${displayedMonth.year}年${displayedMonth.month + 1}月",
                    color = ColorFg,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "›",
                    color = if (monthOffset < 0) ColorGood else ColorCard2,
                    fontSize = 22.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(enabled = monthOffset < 0) { monthOffset++; selectedKey = null }
                        .padding(horizontal = 10.dp)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("本月打卡 $checkedDays 天", color = ColorMuted, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))

        // 星期表头(周一开头)
        Row(modifier = Modifier.fillMaxWidth()) {
            for (d in WeekdayLabels) {
                Text(
                    d,
                    color = ColorMuted,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(6.dp))

        // 日历格:总格数 = 空位 + 天数,按 7 列铺
        val totalCells = displayedMonth.leadingBlanks + displayedMonth.days.size
        val rows = (totalCells + 6) / 7
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (r in 0 until rows) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (c in 0 until 7) {
                        val idx = r * 7 + c
                        val dayIndex = idx - displayedMonth.leadingBlanks
                        if (dayIndex in displayedMonth.days.indices) {
                            val date = displayedMonth.days[dayIndex]
                            val key = date.key
                            val secs = secsOf(key)
                            val isToday = key == todayKey
                            val isSelected = key == selectedKey
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(HeatCellShape)
                                    .background(heatColor(secs))
                                    .then(
                                        when {
                                            isSelected -> Modifier.border(1.5.dp, ColorFg, HeatCellShape)
                                            isToday -> Modifier.border(1.5.dp, ColorGood, HeatCellShape)
                                            else -> Modifier
                                        }
                                    )
                                    .clickable { selectedKey = if (isSelected) null else key },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "${date.number}",
                                    color = if (secs >= 3 * 3600) ColorBg else ColorMuted,
                                    fontSize = 11.sp
                                )
                            }
                        } else {
                            Spacer(Modifier.weight(1f).aspectRatio(1f))
                        }
                    }
                }
            }
        }

        // 图例
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("少", color = ColorMuted, fontSize = 11.sp)
            for (secs in HeatLegendSeconds) {
                Box(
                    Modifier
                        .padding(horizontal = 2.dp)
                        .size(12.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(heatColor(secs))
                )
            }
            Text("多", color = ColorMuted, fontSize = 11.sp)
        }

        // 选中某天:当天总时长 + 各科分布
        selectedKey?.let { key ->
            val total = secsOf(key)
            Spacer(Modifier.height(10.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(ColorCard2)
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(key, color = ColorFg, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text(
                        if (total > 0) hm(total) else "未学习",
                        color = if (total > 0) ColorGood else ColorMuted,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                // 历史日期没有实时增量，缓存明细；仅今日确有在途计时时按秒刷新。
                val liveBreakdownTick = if (key == todayKey && hasLiveTiming) now else 0L
                val bySubject = remember(
                    key,
                    state.dailySub,
                    state.subjects,
                    state.pomo,
                    liveBreakdownTick
                ) {
                    vm.daySubjectBreakdown(key, now)
                }
                if (bySubject.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    for (slice in bySubject) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(slice.name, color = ColorMuted, fontSize = 12.sp)
                            Text(hm(slice.secs), color = ColorFg, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
