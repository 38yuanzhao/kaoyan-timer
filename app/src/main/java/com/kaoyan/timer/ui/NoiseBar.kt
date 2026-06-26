package com.kaoyan.timer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaoyan.timer.audio.AudioEngine

@Composable
fun NoiseBar(
    audio: AudioEngine,
    modifier: Modifier = Modifier
) {
    var noiseType by remember { mutableStateOf<String?>(null) }
    var volume by remember { mutableFloatStateOf(0.5f) }

    SectionCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("🔊", fontSize = 20.sp)
            FilterChip(
                selected = noiseType == "white",
                onClick = {
                    noiseType = if (noiseType == "white") null else "white"
                    audio.setNoise(noiseType, volume)
                },
                label = { Text("白噪音") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = ColorAccent2,
                    selectedLabelColor = ColorBg,
                    labelColor = ColorFg
                )
            )
            FilterChip(
                selected = noiseType == "brown",
                onClick = {
                    noiseType = if (noiseType == "brown") null else "brown"
                    audio.setNoise(noiseType, volume)
                },
                label = { Text("棕噪音") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = ColorAccent,
                    selectedLabelColor = ColorBg,
                    labelColor = ColorFg
                )
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("音量", color = ColorMuted, fontSize = 12.sp)
            Slider(
                value = volume,
                onValueChange = {
                    volume = it
                    audio.setVolume(it)
                    if (noiseType != null) audio.setNoise(noiseType, it)
                },
                valueRange = 0f..1f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = ColorGood,
                    activeTrackColor = ColorGood,
                    inactiveTrackColor = ColorCard2
                )
            )
            Text("${(volume * 100).toInt()}%", color = ColorMuted, fontSize = 12.sp)
        }
    }
}
