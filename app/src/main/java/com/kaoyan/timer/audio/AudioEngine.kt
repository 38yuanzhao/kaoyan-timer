package com.kaoyan.timer.audio

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * 音频引擎:beep 提示音 + 白/棕噪音持续播放 + 振动。
 * 线程安全,切换/停止不崩。
 */
class AudioEngine(private val context: Context) {

    private val sampleRate = 44100

    // ---- 噪音相关 ----
    @Volatile private var noiseTrack: AudioTrack? = null
    @Volatile private var noiseThread: Thread? = null
    @Volatile private var noiseRunning = false
    @Volatile private var currentNoiseType: String? = null
    @Volatile private var volume: Float = 1.0f

    private val lock = Any()

    // ---------------------------------------------------------------
    // beep:880Hz 短音 + 振动
    // ---------------------------------------------------------------
    fun beep() {
        try {
            playToneBeep()
        } catch (_: Throwable) {
            // 忽略音频异常,保证不崩
        }
        try {
            vibrate()
        } catch (_: Throwable) {
            // 忽略振动异常
        }
    }

    private fun playToneBeep() {
        val freq = 880.0
        val durationMs = 220
        val numSamples = sampleRate * durationMs / 1000
        val buffer = ShortArray(numSamples)
        val twoPiF = 2.0 * PI * freq / sampleRate
        for (i in 0 until numSamples) {
            // 简单的淡入淡出包络,避免爆音
            val env = when {
                i < numSamples * 0.1 -> i / (numSamples * 0.1)
                i > numSamples * 0.9 -> (numSamples - i) / (numSamples * 0.1)
                else -> 1.0
            }
            val sample = sin(twoPiF * i) * env * 0.6
            buffer[i] = (sample * Short.MAX_VALUE).toInt().toShort()
        }

        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufSizeBytes = maxOf(minBuf, buffer.size * 2)

        val track = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            AudioTrack.Builder()
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufSizeBytes)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
        } else {
            @Suppress("DEPRECATION")
            AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufSizeBytes,
                AudioTrack.MODE_STATIC
            )
        }

        track.write(buffer, 0, buffer.size)
        track.setVolume(1.0f)
        track.play()

        // 在后台线程延迟释放,避免阻塞调用方
        val playMs = durationMs.toLong() + 80L
        Thread {
            try {
                Thread.sleep(playMs)
            } catch (_: InterruptedException) {
            }
            try {
                track.stop()
            } catch (_: Throwable) {
            }
            try {
                track.release()
            } catch (_: Throwable) {
            }
        }.apply { isDaemon = true }.start()
    }

    private fun vibrate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            val vibrator = manager?.defaultVibrator
            vibrator?.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(200)
            }
        }
    }

    // ---------------------------------------------------------------
    // 噪音:type "white"/"brown"/null
    // ---------------------------------------------------------------
    fun setNoise(type: String?, vol: Float) {
        synchronized(lock) {
            volume = vol.coerceIn(0f, 1f)
            if (type == null) {
                stopNoiseLocked()
                currentNoiseType = null
                return
            }
            if (type == currentNoiseType && noiseRunning) {
                // 类型未变,仅更新音量
                noiseTrack?.let { safeSetVolume(it, volume) }
                return
            }
            // 切换类型:先停旧的,再启动新的
            stopNoiseLocked()
            currentNoiseType = type
            startNoiseLocked(type)
        }
    }

    fun setVolume(vol: Float) {
        synchronized(lock) {
            volume = vol.coerceIn(0f, 1f)
            noiseTrack?.let { safeSetVolume(it, volume) }
        }
    }

    private fun safeSetVolume(track: AudioTrack, vol: Float) {
        try {
            track.setVolume(vol)
        } catch (_: Throwable) {
        }
    }

    private fun startNoiseLocked(type: String) {
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufSizeBytes = maxOf(minBuf, 4096)

        val track = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            AudioTrack.Builder()
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufSizeBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } else {
            @Suppress("DEPRECATION")
            AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufSizeBytes,
                AudioTrack.MODE_STREAM
            )
        }

        safeSetVolume(track, volume)
        try {
            track.play()
        } catch (_: Throwable) {
            try { track.release() } catch (_: Throwable) {}
            return
        }

        noiseTrack = track
        noiseRunning = true

        val chunkSamples = 2048
        val thread = Thread {
            val chunk = ShortArray(chunkSamples)
            var last = 0.0
            while (noiseRunning) {
                when (type) {
                    "white" -> {
                        for (i in 0 until chunkSamples) {
                            val w = Random.nextDouble(-1.0, 1.0)
                            chunk[i] = (w * 0.5 * Short.MAX_VALUE).toInt().toShort()
                        }
                    }
                    "brown" -> {
                        for (i in 0 until chunkSamples) {
                            val w = Random.nextDouble(-1.0, 1.0)
                            last = (last + 0.02 * w) / 1.02 * 3.5
                            val v = last.coerceIn(-1.0, 1.0)
                            chunk[i] = (v * 0.5 * Short.MAX_VALUE).toInt().toShort()
                        }
                    }
                    else -> {
                        for (i in 0 until chunkSamples) chunk[i] = 0
                    }
                }
                val t = noiseTrack ?: break
                try {
                    t.write(chunk, 0, chunkSamples)
                } catch (_: Throwable) {
                    break
                }
            }
        }.apply { isDaemon = true }
        noiseThread = thread
        thread.start()
    }

    private fun stopNoiseLocked() {
        noiseRunning = false
        val thread = noiseThread
        noiseThread = null
        val track = noiseTrack
        noiseTrack = null
        if (thread != null) {
            try {
                thread.join(500)
            } catch (_: InterruptedException) {
            }
        }
        if (track != null) {
            try { track.stop() } catch (_: Throwable) {}
            try { track.release() } catch (_: Throwable) {}
        }
    }

    fun release() {
        synchronized(lock) {
            stopNoiseLocked()
            currentNoiseType = null
        }
    }
}
