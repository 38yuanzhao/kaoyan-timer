package com.kaoyan.timer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaoyan.timer.KaoyanViewModel
import androidx.compose.material3.Text

@Composable
fun ChartCard(
    vm: KaoyanViewModel,
    now: Long,
    modifier: Modifier = Modifier
) {
    val bars = vm.last7(now)
    val maxSecs = (bars.maxOfOrNull { it.secs } ?: 0.0).coerceAtLeast(1.0)
    val dowNames = arrayOf("日", "一", "二", "三", "四", "五", "六")

    SectionCard(modifier = modifier) {
        Text(
            "近7天投入(绿色为今日)",
            color = ColorFg,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(12.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        ) {
            val n = bars.size.coerceAtLeast(1)
            val labelTopPx = 22f      // 柱上小时数字区域
            val labelBottomPx = 26f   // 柱下星期区域
            val chartHeight = size.height - labelTopPx - labelBottomPx
            val slot = size.width / n
            val barWidth = slot * 0.5f

            val accentColor = ColorAccent2
            val goodColor = ColorGood

            bars.forEachIndexed { i, bar ->
                val ratio = (bar.secs / maxSecs).toFloat().coerceIn(0f, 1f)
                val barH = chartHeight * ratio
                val cx = slot * i + slot / 2f
                val left = cx - barWidth / 2f
                val top = labelTopPx + (chartHeight - barH)

                val color: Color = if (bar.today) goodColor else accentColor
                drawRoundedBar(left, top, barWidth, barH, color)

                // 柱上小时数
                val hours = bar.secs / 3600.0
                if (hours > 0.0) {
                    drawNativeText(
                        "%.1f".format(hours),
                        cx,
                        labelTopPx + (chartHeight - barH) - 6f,
                        ColorFg.toArgb(),
                        9f,
                        centerX = true
                    )
                }

                // 柱下星期
                val label = if (bar.today) "今" else dowNames[bar.dow.coerceIn(0, 6)]
                drawNativeText(
                    label,
                    cx,
                    size.height - 6f,
                    (if (bar.today) ColorGood else ColorMuted).toArgb(),
                    11f,
                    centerX = true
                )
            }
        }
    }
}

private fun DrawScope.drawRoundedBar(
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    color: Color
) {
    val h = height.coerceAtLeast(2f)
    drawRoundRect(
        color = color,
        topLeft = Offset(left, top.coerceAtMost(size.height)),
        size = Size(width, h),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
    )
}

private fun DrawScope.drawNativeText(
    text: String,
    x: Float,
    y: Float,
    colorArgb: Int,
    spSize: Float,
    centerX: Boolean
) {
    val paint = android.graphics.Paint().apply {
        color = colorArgb
        textSize = spSize * density
        isAntiAlias = true
        textAlign = if (centerX) android.graphics.Paint.Align.CENTER else android.graphics.Paint.Align.LEFT
    }
    drawContext.canvas.nativeCanvas.drawText(text, x, y, paint)
}
