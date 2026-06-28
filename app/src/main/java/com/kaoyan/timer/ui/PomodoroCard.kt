package com.kaoyan.timer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextDecoration
import com.kaoyan.timer.KaoyanViewModel
import com.kaoyan.timer.model.AppState
import com.kaoyan.timer.model.Item
import com.kaoyan.timer.model.Pomo
import com.kaoyan.timer.model.Subtask
import com.kaoyan.timer.util.mmss
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PomodoroCard(
    vm: KaoyanViewModel,
    state: AppState,
    now: Long,
    modifier: Modifier = Modifier
) {
    val options = remember(state.subjects) {
        state.subjects.flatMap { s ->
            s.items.map { it -> it.id to "${s.name} · ${it.name}" }
        }
    }

    val pomoRunning = state.pomo != null
    var selectedId by remember(state.subjects) {
        mutableStateOf(
            state.pomo?.itemId
                ?: state.lastPomoItemId?.takeIf { id -> options.any { it.first == id } }
                ?: options.firstOrNull()?.first
        )
    }
    if (pomoRunning && state.pomo?.itemId != null) {
        selectedId = state.pomo?.itemId
    }
    var expanded by remember { mutableStateOf(false) }
    var showDurationDialog by remember { mutableStateOf(false) }
    var showBreakdown by remember { mutableStateOf(false) }

    val selItem = state.subjects.flatMap { it.items }.firstOrNull { it.id == selectedId }
    val selectedLabel = options.firstOrNull { it.first == selectedId }?.second ?: "选择子项"

    val isBreak = state.pomo?.phase == "break"
    val isOvertime = state.pomo?.phase == "overtime"
    val paused = state.pomo?.pausedAt != null
    val remainSec: Long = if (pomoRunning) {
        vm.pomoRemainSec(now)
    } else {
        state.focusMin.toLong() * 60L
    }
    val totalSec: Long = if (pomoRunning) {
        ((state.pomo!!.endsAt - state.pomo!!.startAt) / 1000L).coerceAtLeast(1L)
    } else {
        state.focusMin.toLong() * 60L
    }
    val progress = if (pomoRunning) 1f - remainSec.toFloat() / totalSec.toFloat() else 0f
    val ringColor = if (isBreak) ColorAccent2 else ColorGood
    val phaseLabel = when {
        paused -> "已暂停"
        isOvertime -> "超时中 · 点完成结束"
        state.pomo?.phase == "focus" -> "专注中"
        state.pomo?.phase == "break" -> "休息中"
        else -> "待开始 · 点击数字改时长"
    }

    val runningBg = if (state.pomo?.phase == "focus") ColorGoodContainer else ColorCard
    SectionCard(modifier = modifier, containerColor = runningBg) {
        SectionTitle("番茄钟")
        Spacer(Modifier.height(12.dp))

        ExposedDropdownMenuBox(
            expanded = expanded && !pomoRunning,
            onExpandedChange = { if (!pomoRunning) expanded = it }
        ) {
            OutlinedTextField(
                value = selectedLabel,
                onValueChange = {},
                readOnly = true,
                enabled = !pomoRunning,
                label = { Text("子项") },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && !pomoRunning)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = expanded && !pomoRunning,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { opt ->
                    DropdownMenuItem(
                        text = { Text(opt.second, color = ColorFg) },
                        onClick = {
                            selectedId = opt.first
                            vm.selectPomoItem(opt.first) // 持久化选择,重开后恢复
                            expanded = false
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        val timeStr = if (isOvertime) "+" + mmss(vm.pomoOvertimeSec(now)) else mmss(remainSec)
        BoxWithConstraints(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val ringD = if (maxWidth < 260.dp) maxWidth else 260.dp
            val fs = ringD.value * 0.205f
            val centerModifier = if (!pomoRunning) {
                Modifier.size(ringD).clickable { showDurationDialog = true }
            } else {
                Modifier.size(ringD)
            }
            Box(modifier = centerModifier, contentAlignment = Alignment.Center) {
                RingProgress(
                    progress = progress,
                    color = ringColor,
                    strokeWidth = 12.dp,
                    modifier = Modifier.fillMaxSize()
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        timeStr,
                        color = ringColor,
                        fontSize = fs.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(phaseLabel, color = if (pomoRunning) ColorFg else ColorMuted, fontSize = 13.sp)
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        if (!pomoRunning) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { showBreakdown = true },
                    enabled = selItem != null,
                    modifier = Modifier.weight(1f).height(52.dp)
                ) {
                    val n = selItem?.subtasks?.size ?: 0
                    Text(if (n > 0) "拆解 · $n" else "拆解任务", color = ColorGood, fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = { selectedId?.let { vm.startPomo(it) } },
                    enabled = selectedId != null,
                    modifier = Modifier.weight(1.4f).height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ColorGood,
                        contentColor = ColorBg
                    )
                ) { Text("开始 ▶", fontWeight = FontWeight.SemiBold) }
            }
        } else {
            val chainSub = state.pomo?.subtaskId
            PomoControls(
                paused = paused,
                chainActive = chainSub != null && selItem != null && !isBreak,
                onComplete = { chainSub?.let { selItem?.let { item -> vm.markSubtaskDone(item.id, it) } } },
                onPauseToggle = { if (paused) vm.resumePomo() else vm.pausePomo() },
                onStop = { vm.stopPomo() }
            )
            // 拆解链运行态:卡片内清单条
            if (state.pomo?.subtaskId != null && selItem != null) {
                Spacer(Modifier.height(16.dp))
                ChainChecklist(vm, selItem, state.pomo)
            }
        }
    }

    if (showBreakdown && selItem != null) {
        BreakdownSheet(
            vm = vm,
            item = selItem,
            onDismiss = { showBreakdown = false },
            onStart = { vm.startSubtaskChain(selItem.id) }
        )
    }

    if (showDurationDialog) {
        PomoDurationDialog(
            focusMin = state.focusMin,
            breakMin = state.breakMin,
            onFocusChange = { vm.setFocusMin(it) },
            onBreakChange = { vm.setBreakMin(it) },
            onDismiss = { showDurationDialog = false }
        )
    }
}

/** 点番茄大数字弹出:用与设置页同款滚轮调专注/休息时长,实时生效。 */
@Composable
private fun PomoDurationDialog(
    focusMin: Int,
    breakMin: Int,
    onFocusChange: (Int) -> Unit,
    onBreakChange: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("番茄时长", color = ColorFg) },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                WheelMinutePicker(
                    label = "专注(分)",
                    value = focusMin,
                    range = 1..180,
                    enabled = true,
                    onChange = onFocusChange,
                    modifier = Modifier.weight(1f)
                )
                WheelMinutePicker(
                    label = "休息(分)",
                    value = breakMin,
                    range = 1..60,
                    enabled = true,
                    onChange = onBreakChange,
                    modifier = Modifier.weight(1f)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成", color = ColorGood) }
        },
        containerColor = ColorCard2,
        titleContentColor = ColorFg,
        textContentColor = ColorFg
    )
}

@Composable
fun PomoControls(
    paused: Boolean,
    chainActive: Boolean,
    onComplete: () -> Unit,
    onPauseToggle: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    inlineStop: Boolean = true
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (chainActive) {
            Button(
                onClick = onComplete,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ColorGood, contentColor = ColorBg)
            ) { Text("完成本段 ✓", fontWeight = FontWeight.SemiBold) }
            Spacer(Modifier.height(10.dp))
        }

        if (inlineStop) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PomoPauseButton(paused, onPauseToggle, outlined = chainActive, Modifier.weight(1f))
                PomoStopButton(onStop, outlined = chainActive, Modifier.weight(1f))
            }
        } else {
            PomoPauseButton(paused, onPauseToggle, outlined = chainActive, Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            PomoStopButton(onStop, outlined = true, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun PomoPauseButton(
    paused: Boolean,
    onClick: () -> Unit,
    outlined: Boolean,
    modifier: Modifier
) {
    val text = if (paused) "继续 ▶" else "暂停 ❚❚"
    if (outlined) {
        OutlinedButton(onClick = onClick, modifier = modifier.height(52.dp)) {
            Text(text, color = ColorGood, fontWeight = FontWeight.SemiBold)
        }
    } else {
        Button(
            onClick = onClick,
            modifier = modifier.height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ColorGood, contentColor = ColorBg)
        ) { Text(text, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun PomoStopButton(
    onClick: () -> Unit,
    outlined: Boolean,
    modifier: Modifier
) {
    if (outlined) {
        OutlinedButton(onClick = onClick, modifier = modifier.height(52.dp)) {
            Text("停止", color = ColorAccent, fontWeight = FontWeight.SemiBold)
        }
    } else {
        Button(
            onClick = onClick,
            modifier = modifier.height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ColorAccent, contentColor = ColorBg)
        ) { Text("停止", fontWeight = FontWeight.SemiBold) }
    }
}

/** 拆解链清单条:竖排小任务,当前高亮、已完成划线、可手动打勾。卡片态与全屏专注共用。 */
@Composable
fun ChainChecklist(vm: KaoyanViewModel, item: Item, pomo: Pomo?, modifier: Modifier = Modifier) {
    val subs = item.subtasks
    if (subs.isEmpty()) return
    val remaining = subs.count { !it.done }
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionTitle("清单条")
            Text("还剩 $remaining 个", color = ColorMuted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(8.dp))
        subs.forEach { st ->
            val current = pomo?.subtaskId == st.id && (pomo.phase == "focus" || pomo.phase == "overtime")
            val marker = when { st.done -> "✓"; current -> "▶"; else -> "○" }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !st.done) { vm.markSubtaskDone(item.id, st.id) }
                    .padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    marker,
                    color = if (st.done) ColorAccent2 else if (current) ColorGood else ColorMuted,
                    fontSize = 15.sp
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    st.name,
                    color = if (st.done) ColorMuted else if (current) ColorGood else ColorFg,
                    fontSize = 15.sp,
                    fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                    textDecoration = if (st.done) TextDecoration.LineThrough else null,
                    modifier = Modifier.weight(1f)
                )
                Text("${st.estMin} 分", color = ColorMuted, fontSize = 12.sp)
            }
        }
    }
}

/** 拆解面板:底部弹出,编辑某子项的小任务(名字 + 预估时长),开始即连跑。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BreakdownSheet(
    vm: KaoyanViewModel,
    item: Item,
    onDismiss: () -> Unit,
    onStart: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val rows = remember { mutableStateListOf<Subtask>().apply { addAll(item.subtasks) } }
    var newName by remember { mutableStateOf("") }
    var timeEditId by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = { vm.saveSubtasks(item.id, rows.toList()); onDismiss() },
        sheetState = sheetState,
        containerColor = ColorCard2
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            Text("拆解任务 · ${item.name}", color = ColorFg, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text("拆成几段,每段填你估的时间", color = ColorMuted, fontSize = 13.sp)
            Spacer(Modifier.height(14.dp))

            rows.forEachIndexed { idx, st ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(st.name, color = ColorFg, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Text(
                        "${st.estMin} 分",
                        color = ColorGood,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .border(0.5.dp, ColorLine, RoundedCornerShape(8.dp))
                            .clickable { timeEditId = st.id }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                    Text(
                        "✕",
                        color = ColorAccent,
                        fontSize = 15.sp,
                        modifier = Modifier.clickable { rows.removeAt(idx) }.padding(6.dp)
                    )
                }
            }

            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                placeholder = { Text("+ 加小任务,点右侧确认", color = ColorMuted) },
                singleLine = true,
                trailingIcon = {
                    TextButton(
                        onClick = {
                            val nm = newName.trim()
                            if (nm.isNotEmpty()) {
                                rows.add(Subtask(UUID.randomUUID().toString(), nm, 25))
                                newName = ""
                            }
                        },
                        enabled = newName.isNotBlank()
                    ) { Text("添加", color = ColorGood) }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("共 ${rows.size} 个小任务", color = ColorMuted, fontSize = 13.sp)
                Text("预计 ${rows.sumOf { it.estMin }} 分钟", color = ColorMuted, fontSize = 13.sp)
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { vm.saveSubtasks(item.id, rows.toList()); onStart(); onDismiss() },
                enabled = rows.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ColorGood, contentColor = ColorBg)
            ) { Text("开始专注 ▶", fontWeight = FontWeight.SemiBold) }
        }
    }

    timeEditId?.let { sid ->
        val idx = rows.indexOfFirst { it.id == sid }
        if (idx >= 0) {
            SubtaskTimeDialog(
                initial = rows[idx].estMin,
                onDismiss = { timeEditId = null },
                onConfirm = { m -> rows[idx] = rows[idx].copy(estMin = m); timeEditId = null }
            )
        } else timeEditId = null
    }
}

/** 单个小任务的预估时长滚轮对话框(复用 WheelMinutePicker)。 */
@Composable
private fun SubtaskTimeDialog(initial: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var v by remember { mutableStateOf(initial.coerceIn(5, 120)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("预估时长", color = ColorFg) },
        text = {
            WheelMinutePicker(
                label = "分钟",
                value = v,
                range = 5..120,
                enabled = true,
                onChange = { v = it },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(v) }) { Text("完成", color = ColorGood) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = ColorMuted) } },
        containerColor = ColorCard2,
        titleContentColor = ColorFg,
        textContentColor = ColorFg
    )
}
