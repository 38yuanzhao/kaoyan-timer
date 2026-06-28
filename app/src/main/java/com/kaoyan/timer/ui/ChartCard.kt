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
import com.kaoyan.timer.util.hmc
import androidx.compose.material3.Text

private val PastBar = ColorMuted.copy(alpha = 0.40f)

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
            // 预留区随屏幕密度缩放(标签 textSize 也是 *density),否则高密度屏柱顶/柱底标签被裁
            val labelTopPx = 26f * density
            val labelBottomPx = 30f * density
            val chartHeight = size.height - labelTopPx - labelBottomPx
            val slot = size.width / n
            val barWidth = slot * 0.5f

            bars.forEachIndexed { i, bar ->
                val ratio = (bar.secs / maxSecs).toFloat().coerceIn(0f, 1f)
                val barH = chartHeight * ratio
                val cx = slot * i + slot / 2f
                val left = cx - barWidth / 2f
                val top = labelTopPx + (chartHeight - barH)

                val color: Color = if (bar.today) ColorGood else PastBar
                drawRoundedBar(left, top, barWidth, barH, color)

                if (bar.secs > 0.0) {
                    drawNativeText(
                        hmc(bar.secs),
                        cx,
                        labelTopPx + (chartHeight - barH) - 6f * density,
                        ColorFg.toArgb(),
                        9f,
                        centerX = true
                    )
                }

                val label = if (bar.today) "今" else dowNames[bar.dow.coerceIn(0, 6)]
                drawNativeText(
                    label,
                    cx,
                    size.height - 7f * density,
                    (if (bar.today) ColorGood else ColorMuted).toArgb(),
                    11f,
                    centerX = true
                )
            }
        }
    }
}

/** 仪表盘速览:7 根迷你柱,无数字无星期,今日绿色。整卡点击由调用方处理。 */
@Composable
fun MiniWeekStrip(
    vm: KaoyanViewModel,
    now: Long,
    modifier: Modifier = Modifier
) {
    val bars = vm.last7(now)
    val maxSecs = (bars.maxOfOrNull { it.secs } ?: 0.0).coerceAtLeast(1.0)
    Canvas(modifier = modifier) {
        val n = bars.size.coerceAtLeast(1)
        val slot = size.width / n
        val barWidth = slot * 0.46f
        bars.forEachIndexed { i, bar ->
            val ratio = (bar.secs / maxSecs).toFloat().coerceIn(0f, 1f)
            val barH = (size.height * ratio).coerceAtLeast(3f)
            val cx = slot * i + slot / 2f
            val left = cx - barWidth / 2f
            val top = size.height - barH
            drawRoundedBar(left, top, barWidth, barH, if (bar.today) ColorGood else PastBar)
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
