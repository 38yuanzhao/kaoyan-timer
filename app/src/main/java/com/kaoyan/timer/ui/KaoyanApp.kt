package com.kaoyan.timer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kaoyan.timer.KaoyanViewModel
import com.kaoyan.timer.audio.AudioEngine

@Composable
fun KaoyanApp(vm: KaoyanViewModel) {
    val state by vm.state.collectAsState()
    val now by vm.now.collectAsState()

    val context = LocalContext.current
    val audio = remember { AudioEngine(context.applicationContext) }
    DisposableEffect(Unit) {
        onDispose { audio.release() }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = ColorBg) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            contentAlignment = Alignment.TopCenter
        ) {
            androidx.compose.foundation.layout.BoxWithConstraints(
                modifier = Modifier.fillMaxSize()
            ) {
                val twoCol = maxWidth >= 600.dp
                val scroll = rememberScrollState()

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 920.dp)
                        .verticalScroll(scroll)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (twoCol) {
                        // 顶部整宽倒计时
                        CountdownCard(vm, state.examDate, now)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // 左列
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                ProgressCard(vm, state.startDate, now)
                                PomodoroCard(vm, state, now)
                            }
                            // 右列
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                TodayTotalRow(vm, state, now)
                                SubjectsSection(vm, state, now)
                                ChartCard(vm, now)
                            }
                        }
                        NoiseBar(audio)
                    } else {
                        CountdownCard(vm, state.examDate, now)
                        ProgressCard(vm, state.startDate, now)
                        TodayTotalRow(vm, state, now)
                        PomodoroCard(vm, state, now)
                        SubjectsSection(vm, state, now)
                        ChartCard(vm, now)
                        NoiseBar(audio)
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}
