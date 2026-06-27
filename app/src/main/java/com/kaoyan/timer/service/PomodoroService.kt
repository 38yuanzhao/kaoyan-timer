package com.kaoyan.timer.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.kaoyan.timer.MainActivity

/**
 * 番茄运行期间的前台服务:常驻通知 + 保活进程,让锁屏/后台时 ViewModel 的每秒 tick
 * 继续推进(自动接力可靠)。计时逻辑仍在 ViewModel;本服务只负责"别被冻结/杀掉"与展示剩余时间。
 * 进程仍被杀时由 reconcile 安全降级兜底,不会脏数据。
 */
class PomodoroService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "番茄专注"
        val text = intent?.getStringExtra(EXTRA_TEXT) ?: ""
        val notif = buildNotification(this, title, text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notif)
        }
        return START_NOT_STICKY // 被杀不自动重启,交给 reconcile 兜底
    }

    companion object {
        private const val CHANNEL_ID = "pomodoro_running"
        private const val NOTIF_ID = 1001
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_TEXT = "text"

        private fun ensureChannel(ctx: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val mgr = ctx.getSystemService(NotificationManager::class.java)
                if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                    val ch = NotificationChannel(CHANNEL_ID, "番茄计时", NotificationManager.IMPORTANCE_LOW)
                    ch.setShowBadge(false)
                    mgr.createNotificationChannel(ch)
                }
            }
        }

        private fun buildNotification(ctx: Context, title: String, text: String): Notification {
            ensureChannel(ctx)
            val tap = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            return NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(tap)
                .build()
        }

        /** 启动前台服务(链/番茄开始时)。 */
        fun start(ctx: Context, title: String, text: String) {
            val i = Intent(ctx, PomodoroService::class.java)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_TEXT, text)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i)
            else ctx.startService(i)
        }

        /** 每秒更新通知文案(同 id);无通知权限时静默跳过,服务仍保活。 */
        fun update(ctx: Context, title: String, text: String) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) return
            NotificationManagerCompat.from(ctx).notify(NOTIF_ID, buildNotification(ctx, title, text))
        }

        /** 链/番茄结束时停服务。 */
        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, PomodoroService::class.java))
        }
    }
}
