package com.kaoyan.timer.audio

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.kaoyan.timer.R
import kotlin.math.PI
import kotlin.math.sin

/**
 * 音频引擎:beep 提示音(合成) + 白/棕噪音(循环播放本地录音) + 振动。
 * 线程安全,切换/停止不崩。
 */
class AudioEngine(private val context: Context) {

    private val sampleRate = 44100

    // ---- 噪音相关:双 MediaPlayer setNextMediaPlayer 接力,无缝循环 ----
    private var noisePlayer: MediaPlayer? = null   // 当前在放
    private var noiseNext: MediaPlayer? = null     // 已就绪的下一段(接力,消除循环空档)
    @Volatile private var currentNoiseType: String? = null
    private var volume: Float = 1.0f

    private val lock = Any()

    private fun resFor(type: String): Int = when (type) {
        "white" -> R.raw.rain_white     // 雨声
        "brown" -> R.raw.brown_noise    // 棕噪
        else -> 0
    }

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
    // 噪音:type "white"(雨声)/"brown"(棕噪)/null,循环播放本地录音
    // ---------------------------------------------------------------
    fun setNoise(type: String?, vol: Float) {
        synchronized(lock) {
            volume = vol.coerceIn(0f, 1f)
            if (type == null) {
                stopNoiseLocked()
                currentNoiseType = null
                return
            }
            if (type == currentNoiseType && noisePlayer != null) {
                // 类型未变,仅更新音量(当前段 + 已就绪的接力段都要更新)
                noisePlayer?.let { safeSetVolume(it, volume) }
                noiseNext?.let { safeSetVolume(it, volume) }
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
            noisePlayer?.let { safeSetVolume(it, volume) }
            noiseNext?.let { safeSetVolume(it, volume) }
        }
    }

    private fun safeSetVolume(mp: MediaPlayer, vol: Float) {
        try {
            mp.setVolume(vol, vol)
        } catch (_: Throwable) {
        }
    }

    private fun startNoiseLocked(type: String) {
        val resId = resFor(type)
        if (resId == 0) return
        try {
            // create() 会同步 prepare;本地小文件很快
            val mp = MediaPlayer.create(context, resId) ?: return
            safeSetVolume(mp, volume)
            noisePlayer = mp
            prepareNextLocked(resId) // 预排下一段做无缝接力(不用 isLooping,它在循环点有空档)
            mp.start()
        } catch (_: Throwable) {
            // 创建/播放失败:忽略,保证不崩
        }
    }

    /** 给当前段排一个就绪的后继并 setNextMediaPlayer;当前段播完自动无缝切到后继,再续排下一段。 */
    private fun prepareNextLocked(resId: Int) {
        val cur = noisePlayer ?: return
        val nxt = try { MediaPlayer.create(context, resId) } catch (_: Throwable) { null } ?: return
        safeSetVolume(nxt, volume)
        noiseNext = nxt
        try { cur.setNextMediaPlayer(nxt) } catch (_: Throwable) {}
        cur.setOnCompletionListener { done ->
            synchronized(lock) {
                if (done !== noisePlayer) return@synchronized // 已被停止/切换,忽略
                try { done.release() } catch (_: Throwable) {}
                // 后继已由系统自动开始播放,提升为当前段
                noisePlayer = noiseNext
                noiseNext = null
                val t = currentNoiseType
                if (t != null && noisePlayer != null && resFor(t) == resId) {
                    prepareNextLocked(resId) // 继续接力
                } else {
                    stopNoiseLocked() // 期间被停/切了:收掉刚提升的这段
                }
            }
        }
    }

    private fun stopNoiseLocked() {
        val mp = noisePlayer
        val nx = noiseNext
        noisePlayer = null
        noiseNext = null
        if (mp != null) {
            try { mp.setOnCompletionListener(null) } catch (_: Throwable) {}
            try { mp.stop() } catch (_: Throwable) {}
            try { mp.release() } catch (_: Throwable) {}
        }
        if (nx != null) {
            try { nx.stop() } catch (_: Throwable) {}
            try { nx.release() } catch (_: Throwable) {}
        }
    }

    fun release() {
        synchronized(lock) {
            stopNoiseLocked()
            currentNoiseType = null
        }
    }
}
