package com.kaoyan.timer.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import com.kaoyan.timer.KaoyanViewModel
import com.kaoyan.timer.model.AppState
import com.kaoyan.timer.model.Item
import com.kaoyan.timer.model.Subject
import com.kaoyan.timer.util.fmt

/** 专注页的科目计时列表:运行中科目自动置顶。 */
@Composable
fun SubjectTimerList(
    vm: KaoyanViewModel,
    state: AppState,
    now: Long,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (state.subjects.isEmpty()) {
            SectionCard {
                Text("还没有选择科目模板", color = ColorFg, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Text("到「设置」选择 11408 / 22048 模板后开始计时", color = ColorMuted, fontSize = 13.sp)
            }
        } else {
            val ordered = state.subjects.sortedByDescending { sub ->
                sub.items.any { it.runningSince != null }
            }
            ordered.forEach { subject ->
                SubjectCard(vm, state, subject, now)
            }
        }
    }
}

/** 模板选择(无模板时)。供设置页使用。 */
@Composable
fun TemplateChooser(vm: KaoyanViewModel) {
    SectionCard {
        SectionTitle("选择考研科目模板")
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { vm.applyTemplate("11408") },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ColorGood,
                    contentColor = ColorBg
                )
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("11408 学硕", fontWeight = FontWeight.SemiBold)
                    Text("数一/408/英一", fontSize = 11.sp)
                }
            }
            Button(
                onClick = { vm.applyTemplate("22048") },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ColorAccent2,
                    contentColor = ColorBg
                )
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("22048 专硕", fontWeight = FontWeight.SemiBold)
                    Text("数二/408/英二", fontSize = 11.sp)
                }
            }
        }
    }
}

/** 当前模板 + 切换入口(破坏性,带确认)。供设置页使用。 */
@Composable
fun TemplateHeader(vm: KaoyanViewModel, state: AppState) {
    var showConfirm by remember { mutableStateOf(false) }
    var showChooser by remember { mutableStateOf(false) }

    val label = when (state.template) {
        "11408" -> "11408 学硕"
        "22048" -> "22048 专硕"
        else -> state.template ?: "自定义"
    }

    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("模板 · $label", color = ColorFg, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            OutlinedButton(onClick = { showConfirm = true }) {
                Text("切换模板", color = ColorAccent)
            }
        }

        if (showChooser) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { vm.applyTemplate("11408"); showChooser = false },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = ColorGood, contentColor = ColorBg)
                ) { Text("11408 学硕") }
                Button(
                    onClick = { vm.applyTemplate("22048"); showChooser = false },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = ColorAccent2, contentColor = ColorBg)
                ) { Text("22048 专硕") }
            }
        }
    }

    if (showConfirm) {
        ConfirmDialog(
            title = "切换模板",
            message = "切换模板会重置各科目时长(累计每日记录仍保留),确定继续?",
            confirmText = "继续",
            onDismiss = { showConfirm = false },
            onConfirm = {
                showConfirm = false
                showChooser = true
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubjectCard(
    vm: KaoyanViewModel,
    state: AppState,
    subject: Subject,
    now: Long
) {
    val multi = subject.items.size > 1

    val selId = state.sel[subject.name] ?: subject.items.firstOrNull()?.id
    val current: Item? = subject.items.firstOrNull { it.id == selId } ?: subject.items.firstOrNull()

    val running = current?.runningSince != null
    var showManual by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    val border = if (running) BorderStroke(1.dp, ColorGood) else null
    val bg = if (running) ColorGoodContainer else ColorCard

    SectionCard(border = border, containerColor = bg) {
        if (!multi) {
            Text(subject.name, color = ColorFg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                current?.let { fmt(vm.itemSeconds(it, now)) } ?: "0h 0m",
                color = if (running) ColorGood else ColorFg,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        } else {
            Text(subject.name, color = ColorFg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                OutlinedTextField(
                    value = current?.let { "${subject.name} · ${it.name}" } ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("当前子项") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    subject.items.forEach { it ->
                        DropdownMenuItem(
                            text = { Text("${subject.name} · ${it.name}", color = ColorFg) },
                            onClick = {
                                vm.selectSubItem(subject.name, it.id)
                                expanded = false
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                current?.let { fmt(vm.itemSeconds(it, now)) } ?: "0h 0m",
                color = if (running) ColorGood else ColorFg,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { current?.let { vm.toggleItem(it.id) } },
                enabled = current != null,
                modifier = Modifier.weight(1f).height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (running) ColorAccent else ColorGood,
                    contentColor = ColorBg
                )
            ) {
                Text(if (running) "暂停" else "▶ 开始", fontWeight = FontWeight.SemiBold)
            }
            OutlinedButton(
                onClick = { showManual = true },
                enabled = current != null
            ) {
                Text("手动", color = ColorAccent2)
            }
        }
    }

    if (showManual && current != null) {
        ManualAddDialog(
            title = "手动调整 · ${subject.name}${if (multi) " · ${current.name}" else ""}",
            onDismiss = { showManual = false },
            onConfirm = { minutes ->
                vm.manualAdd(current.id, minutes)
                showManual = false
            }
        )
    }
}
