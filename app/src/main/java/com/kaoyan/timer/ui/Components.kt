package com.kaoyan.timer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 共用卡片容器 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    border: androidx.compose.foundation.BorderStroke? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ColorCard),
        border = border
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = ColorMuted,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = modifier
    )
}

/** 手动加时间对话框,分钟数(可负) */
@Composable
fun ManualAddDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = ColorFg) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("输入分钟数(可为负数,用于扣减)", color = ColorMuted, fontSize = 13.sp)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    placeholder = { Text("例如 30 或 -15", color = ColorMuted) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val v = text.trim().toDoubleOrNull()
                if (v != null) onConfirm(v)
            }) { Text("确定", color = ColorGood) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = ColorMuted) }
        },
        containerColor = ColorCard2,
        titleContentColor = ColorFg,
        textContentColor = ColorFg
    )
}

/** 简单确认对话框 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String = "确定",
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = ColorFg) },
        text = { Text(message, color = ColorFg) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmText, color = ColorAccent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = ColorMuted) }
        },
        containerColor = ColorCard2,
        titleContentColor = ColorFg,
        textContentColor = ColorFg
    )
}

/** 简单步进器:- 值 + */
@Composable
fun Stepper(
    label: String,
    value: Int,
    enabled: Boolean,
    onChange: (Int) -> Unit,
    min: Int = 1,
    max: Int = 180
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = ColorMuted, fontSize = 12.sp)
        Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TextButton(
                onClick = { if (value > min) onChange(value - 1) },
                enabled = enabled
            ) { Text("-", color = if (enabled) ColorFg else ColorMuted, fontSize = 18.sp) }
            Text(
                "$value",
                color = ColorFg,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            TextButton(
                onClick = { if (value < max) onChange(value + 1) },
                enabled = enabled
            ) { Text("+", color = if (enabled) ColorFg else ColorMuted, fontSize = 18.sp) }
        }
    }
}
