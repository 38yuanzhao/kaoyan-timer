package com.kaoyan.timer.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaoyan.timer.KaoyanViewModel
import com.kaoyan.timer.audio.AudioEngine
import com.kaoyan.timer.model.AppState
import com.kaoyan.timer.util.TimeUtil
import com.kaoyan.timer.util.fmt
import com.kaoyan.timer.util.hm
import com.kaoyan.timer.util.mmss

@Composable
private fun screenModifier() = Modifier
    .fillMaxSize()
    .verticalScroll(rememberScrollState())

// ── Tab 0:仪表盘 ──────────────────────────────────────────────────
@Composable
fun DashboardScreen(
    vm: KaoyanViewModel,
    state: AppState,
    now: Long,
    onStartFocus: () -> Unit,
    onOpenData: () -> Unit
) {
    Column(
        modifier = screenModifier().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(6.dp))
        HeroCountdown(vm, state, now)
        StatTilesGrid(vm, state, now)
        SectionCard(modifier = Modifier.clickable { onOpenData() }) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("近7天投入", color = ColorFg, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text("查看 →", color = ColorMuted, fontSize = 13.sp)
            }
            Spacer(Modifier.height(10.dp))
            MiniWeekStrip(vm, now, Modifier.fillMaxWidth().height(64.dp))
        }
        Button(
            onClick = onStartFocus,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ColorGood, contentColor = ColorBg)
        ) { Text("开始专注 ▶", fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun HeroCountdown(vm: KaoyanViewModel, state: AppState, now: Long) {
    var showHours by remember { mutableStateOf(false) }
    val info = vm.countdown(now)
    val pct = vm.progressPct(now)
    val year = state.examDate.take(4)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = ColorCard),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(Modifier.padding(22.dp)) {
            Text("距 $year 初试", color = ColorMuted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.fillMaxWidth().clickable { showHours = !showHours }
            ) {
                if (!showHours) {
                    Text("${info.days}", color = ColorFg, fontSize = 72.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Text("天", color = ColorMuted, fontSize = 20.sp, modifier = Modifier.padding(bottom = 14.dp))
                } else {
                    val totalMinutes = (info.diffMs / 60000L).coerceAtLeast(0L)
                    val h = totalMinutes / 60
                    val m = totalMinutes % 60
                    Text("${h}时${m}分", color = ColorFg, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(14.dp))
            LinearProgressIndicator(
                progress = { (pct / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = ColorGood,
                trackColor = ColorCard2
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("初试 ${state.examDate}", color = ColorMuted, fontSize = 13.sp)
                Text("进度 ${"%.1f".format(pct)}%", color = ColorMuted, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun StatTilesGrid(vm: KaoyanViewModel, state: AppState, now: Long) {
    val pct = vm.progressPct(now)
    val total = state.subjects.sumOf { s -> s.items.sumOf { vm.itemSeconds(it, now) } }
    val streak = computeStreak(state, now)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("备考进度", "%.0f%%".format(pct), ColorGood, ring = pct / 100f, modifier = Modifier.weight(1f))
            StatTile("今日投入", hm(vm.todaySeconds(now)), ColorGood, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("连续天数", "$streak 天", ColorGood, emoji = "🔥", modifier = Modifier.weight(1f))
            StatTile("累计时长", "${(total / 3600).toInt()}h", ColorFg, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatTile(
    title: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
    ring: Float? = null,
    emoji: String? = null
) {
    Card(
        modifier = modifier.height(104.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ColorCard),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, color = ColorMuted, fontSize = 13.sp, maxLines = 1)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    (if (emoji != null) "$emoji " else "") + value,
                    color = valueColor,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (ring != null) {
                    RingProgress(ring, ColorGood, modifier = Modifier.size(36.dp), strokeWidth = 5.dp)
                }
            }
        }
    }
}

/** 连续天数:从今天往回数连续学习(daily>0)的天数;今日未学习不算断,从昨天接着数。纯 UI 派生,零新 API。 */
private fun computeStreak(state: AppState, now: Long): Int {
    var streak = 0
    var i = 0
    if ((state.daily[TimeUtil.dayKeyOffset(now, 0)] ?: 0.0) <= 0.0) i = 1
    // ponytail: 上限按 daily 实际天数,够用;不引入额外缓存
    while ((state.daily[TimeUtil.dayKeyOffset(now, i)] ?: 0.0) > 0.0) {
        streak++
        i++
    }
    return streak
}

// ── Tab 1:专注 ──────────────────────────────────────────────────
@Composable
fun FocusScreen(
    vm: KaoyanViewModel,
    state: AppState,
    now: Long,
    audio: AudioEngine,
    noiseType: String?,
    volume: Float,
    onNoiseChange: (String?, Float) -> Unit,
    onExpandPomo: () -> Unit
) {
    Column(
        modifier = screenModifier().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(6.dp))
        MiniStatusBar(vm, state, now, onExpandPomo)
        NoiseBar(audio, noiseType, volume, onNoiseChange)
        PomodoroCard(vm, state, now)
        SubjectTimerList(vm, state, now)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun MiniStatusBar(vm: KaoyanViewModel, state: AppState, now: Long, onExpand: () -> Unit) {
    val pomo = state.pomo
    val running = pomo != null
    val pomoText = if (pomo == null) {
        "番茄 · 待开始"
    } else {
        val overtime = pomo.phase == "overtime"
        val label = when {
            pomo.pausedAt != null -> "已暂停"
            overtime -> "超时中"
            pomo.phase == "break" -> "休息中"
            else -> "专注中"
        }
        val timeText = if (overtime) "+" + mmss(vm.pomoOvertimeSec(now)) else mmss(vm.pomoRemainSec(now))
        val chainSuffix = if (pomo.subtaskId != null) {
            val subs = state.subjects.flatMap { it.items }.firstOrNull { it.id == pomo.itemId }?.subtasks ?: emptyList()
            val idx = subs.indexOfFirst { it.id == pomo.subtaskId }
            if (idx >= 0) " · 第 ${idx + 1}/${subs.size}" else ""
        } else ""
        "番茄 · $label $timeText$chainSuffix · 点按全屏"
    }
    SectionCard(modifier = if (running) Modifier.clickable { onExpand() } else Modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("今日 ${hm(vm.todaySeconds(now))}", color = ColorFg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(
                pomoText,
                color = when (pomo?.phase) {
                    "break" -> ColorAccent2
                    null -> ColorMuted
                    else -> ColorGood
                },
                fontSize = 13.sp
            )
        }
    }
}

// ── 番茄运行时:整屏沉浸式专注 ─────────────────────────────────────
@Composable
fun FocusModeScreen(
    vm: KaoyanViewModel,
    state: AppState,
    now: Long,
    onMinimize: () -> Unit,
    onStop: () -> Unit
) {
    val pomo = state.pomo ?: return
    val isBreak = pomo.phase == "break"
    val isOvertime = pomo.phase == "overtime"
    val paused = pomo.pausedAt != null
    val remain = vm.pomoRemainSec(now)
    val total = ((pomo.endsAt - pomo.startAt) / 1000L).coerceAtLeast(1L)
    val progress = 1f - remain.toFloat() / total.toFloat()
    val ringColor = if (isBreak) ColorAccent2 else ColorGood
    val phaseLabel = when {
        paused -> "已暂停"
        isOvertime -> "超时中"
        isBreak -> "休息中"
        else -> "专注中"
    }
    val subjectLabel = remember(state.subjects, pomo.itemId) {
        state.subjects.flatMap { s -> s.items.map { s.name to it } }
            .firstOrNull { it.second.id == pomo.itemId }
            ?.let { "${it.first} · ${it.second.name}" } ?: "专注"
    }
    val chainItem = remember(state.subjects, pomo.itemId) {
        state.subjects.flatMap { s -> s.items }.firstOrNull { it.id == pomo.itemId }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = ColorBg) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onMinimize) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "最小化", tint = ColorMuted)
                }
            }

            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(subjectLabel, color = ColorMuted, fontSize = 15.sp)
                if (pomo.subtaskId != null && chainItem != null) {
                    val subs = chainItem.subtasks
                    val idx = subs.indexOfFirst { it.id == pomo.subtaskId }
                    subs.getOrNull(idx)?.let { cur ->
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "${cur.name} · 第 ${idx + 1}/${subs.size}",
                            color = ColorFg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Spacer(Modifier.height(30.dp))
                val timeStr = if (isOvertime) "+" + mmss(vm.pomoOvertimeSec(now)) else mmss(remain)
                BoxWithConstraints(contentAlignment = Alignment.Center) {
                    // 整屏:环铺满可用宽(上限 360dp),字号随环径走,180:00 也留足余量
                    val ringD = minOf(maxWidth, maxHeight, 360.dp)
                    val fs = ringD.value * 0.205f
                    Box(modifier = Modifier.size(ringD), contentAlignment = Alignment.Center) {
                        RingProgress(progress, ringColor, modifier = Modifier.fillMaxSize(), strokeWidth = 13.dp)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(timeStr, color = ringColor, fontSize = fs.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Text(phaseLabel, color = ColorMuted, fontSize = 15.sp)
                        }
                    }
                }
            }

            if (pomo.subtaskId != null && chainItem != null) {
                ChainChecklist(vm, chainItem, pomo, modifier = Modifier.padding(horizontal = 8.dp))
                Spacer(Modifier.height(14.dp))
            }
            Text("今日已投入 ${hm(vm.todaySeconds(now))}", color = ColorMuted, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
            val chainSub = pomo.subtaskId
            PomoControls(
                paused = paused,
                chainActive = chainSub != null && chainItem != null && !isBreak,
                onComplete = { chainSub?.let { chainItem?.let { item -> vm.markSubtaskDone(item.id, it) } } },
                onPauseToggle = { if (paused) vm.resumePomo() else vm.pausePomo() },
                onStop = onStop,
                inlineStop = false
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ── Tab 2:数据 ──────────────────────────────────────────────────
@Composable
fun DataScreen(vm: KaoyanViewModel, state: AppState, now: Long) {
    Column(
        modifier = screenModifier().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(6.dp))
        ChartCard(vm, now)
        SubjectPieCard(vm, state, now)
        TodayTotalRow(vm, state, now)
        Spacer(Modifier.height(12.dp))
    }
}

// ── Tab 3:设置 ──────────────────────────────────────────────────
@Composable
fun SettingsScreen(vm: KaoyanViewModel, state: AppState) {
    Column(
        modifier = screenModifier().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(6.dp))
        if (state.subjects.isEmpty()) {
            TemplateChooser(vm)
        } else {
            TemplateHeader(vm, state)
        }
        SettingDateRow("初试日期", state.examDate) { vm.setExamDate(it) }
        SettingDateRow("备考起点", state.startDate.ifBlank { "2026-01-01" }) { vm.setStartDate(it) }
        SectionCard {
            SectionTitle("番茄钟默认时长")
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                WheelMinutePicker("专注(分)", state.focusMin, 1..180, true, { vm.setFocusMin(it) }, Modifier.weight(1f))
                WheelMinutePicker("休息(分)", state.breakMin, 1..60, true, { vm.setBreakMin(it) }, Modifier.weight(1f))
            }
        }
        BackupCard(vm)
        Spacer(Modifier.height(12.dp))
    }
}

/** 数据备份 / 多端同步:导出/导入一份 JSON(SAF,无需账号/联网/权限)。 */
@Composable
private fun BackupCard(vm: KaoyanViewModel) {
    val context = LocalContext.current
    var pendingImport by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            val ok = try {
                context.contentResolver.openOutputStream(uri)?.use { it.write(vm.exportJson().toByteArray()) }
                true
            } catch (e: Exception) {
                false
            }
            Toast.makeText(context, if (ok) "已导出备份" else "导出失败", Toast.LENGTH_SHORT).show()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val text = try {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            } catch (e: Exception) {
                null
            }
            if (text.isNullOrBlank()) {
                Toast.makeText(context, "文件无法读取", Toast.LENGTH_SHORT).show()
            } else {
                pendingImport = text
            }
        }
    }

    SectionCard {
        SectionTitle("数据备份 / 同步")
        Spacer(Modifier.height(6.dp))
        Text(
            "导出一份备份,在另一台设备导入即可同步进度;也可存到网盘同步文件夹两端互读。",
            color = ColorMuted,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { exportLauncher.launch("kaoyan-备份.json") },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = ColorGood, contentColor = ColorBg)
            ) { Text("导出备份") }
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) },
                modifier = Modifier.weight(1f)
            ) { Text("导入备份", color = ColorGood) }
        }
    }

    pendingImport?.let { text ->
        ConfirmDialog(
            title = "导入备份",
            message = "导入会用备份覆盖本机当前数据,确定继续?",
            confirmText = "覆盖导入",
            onDismiss = { pendingImport = null },
            onConfirm = {
                val ok = vm.importJson(text)
                Toast.makeText(context, if (ok) "已导入" else "文件无法识别", Toast.LENGTH_SHORT).show()
                pendingImport = null
            }
        )
    }
}

@Composable
private fun SettingDateRow(label: String, date: String, onSet: (String) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(label, color = ColorMuted, fontSize = 13.sp)
                Spacer(Modifier.height(2.dp))
                Text(date, color = ColorFg, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
            OutlinedButton(onClick = { showPicker = true }) {
                Text("修改", color = ColorAccent2)
            }
        }
    }
    if (showPicker) {
        KaoyanDatePickerDialog(
            initialDate = date,
            onDismiss = { showPicker = false },
            onConfirm = {
                onSet(it)
                showPicker = false
            }
        )
    }
}
