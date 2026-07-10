package com.kaoyan.timer.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.RemoteViews
import com.kaoyan.timer.MainActivity
import com.kaoyan.timer.R
import com.kaoyan.timer.data.Store
import com.kaoyan.timer.model.AppState
import com.kaoyan.timer.util.TimeUtil
import com.kaoyan.timer.util.focusIntervalsUntil
import com.kaoyan.timer.util.hm
import java.util.Calendar
import kotlin.math.ceil
import kotlin.math.min

/**
 * 桌面小组件:距初试天数 + 今日投入(有目标则带完成度)。
 * 数据直接读 SharedPreferences;App 内 publish 时主动刷新,系统另按 30 分钟周期兜底。
 * 今日投入使用系统 Chronometer 在桌面进程中走时,无需每秒唤醒 App 或发送广播。
 */
class CountdownWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val state = Store(context).load()
        val now = System.currentTimeMillis()
        for (id in ids) {
            manager.updateAppWidget(id, buildViews(context, state, now))
        }
        scheduleNextMidnight(context, now)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // 墙上时间或时区改变后,重新锚定 elapsedRealtime 并刷新“今天”的边界。
        if (intent.action == Intent.ACTION_TIME_CHANGED ||
            intent.action == Intent.ACTION_TIMEZONE_CHANGED
        ) {
            updateAll(context)
        } else if (intent.action == ACTION_MIDNIGHT_REFRESH) {
            // updateAll 会在刷新完成后续约下一个本地零点。
            updateAll(context)
        }
    }

    override fun onDisabled(context: Context) {
        cancelMidnightRefresh(context)
        super.onDisabled(context)
    }

    companion object {
        private const val ACTION_MIDNIGHT_REFRESH =
            "com.kaoyan.timer.widget.action.MIDNIGHT_REFRESH"
        private const val MIDNIGHT_REQUEST_CODE = 2501

        /** App 数据变更后刷新全部实例。 */
        fun updateAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, CountdownWidget::class.java))
            if (ids.isEmpty()) {
                cancelMidnightRefresh(context)
                return
            }
            val state = Store(context).load()
            val now = System.currentTimeMillis()
            val views = buildViews(context, state, now)
            for (id in ids) mgr.updateAppWidget(id, views)
            scheduleNextMidnight(context, now)
        }

        /**
         * 单个非精确、非唤醒的一次性闹钟:设备醒着时在零点附近刷新;设备休眠时在下次
         * 唤醒后交付。每天只调度一次,不需要精确闹钟权限,也不会为了 Widget 唤醒设备。
         */
        private fun scheduleNextMidnight(context: Context, now: Long) {
            val nextMidnight = Calendar.getInstance().apply {
                timeInMillis = now
                add(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(
                AlarmManager.RTC,
                nextMidnight,
                midnightPendingIntent(context)
            )
        }

        private fun cancelMidnightRefresh(context: Context) {
            context.getSystemService(AlarmManager::class.java)
                .cancel(midnightPendingIntent(context))
        }

        private fun midnightPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, CountdownWidget::class.java)
                .setAction(ACTION_MIDNIGHT_REFRESH)
            return PendingIntent.getBroadcast(
                context,
                MIDNIGHT_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun buildViews(context: Context, state: AppState, now: Long): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_countdown)
            val year = state.examDate.take(4).takeIf { it.length == 4 && it.all(Char::isDigit) } ?: "—"
            views.setTextViewText(R.id.widget_label, context.getString(R.string.widget_exam_label, year))

            val examMs = TimeUtil.dateToMillisLocalMidnight(state.examDate)
            val days = if (examMs > 0L) {
                ceil((examMs - now).coerceAtLeast(0L).toDouble() / TimeUtil.DAY_MS).toInt()
            } else null
            views.setTextViewText(R.id.widget_days, days?.toString() ?: "—")
            views.setContentDescription(
                R.id.widget_days,
                days?.let { context.getString(R.string.widget_days_content_description, it) }
                    ?: context.getString(R.string.widget_exam_date_unset)
            )

            val todaySecs = todaySeconds(state, now)
            val format = if (state.dailyGoalMin > 0) {
                val goalSecs = state.dailyGoalMin * 60.0
                // %%s 先经 getString 还原成 Chronometer 的 %s 占位符。
                context.getString(R.string.widget_today_goal_format, hm(goalSecs))
            } else {
                context.getString(R.string.widget_today_format)
            }
            val liveSources = liveSourceCount(state, now)
            val chronoFormat = if (liveSources > 1) {
                format + context.getString(R.string.widget_multiple_running_suffix, liveSources)
            } else format
            val todayMillis = (todaySecs.coerceAtLeast(0.0) * 1000.0).toLong()
            val base = SystemClock.elapsedRealtime() - todayMillis
            // Chronometer 固定按 1 倍速走;异常的多秒表并发态保持静态,避免显示错误累计值。
            views.setChronometer(R.id.widget_today, base, chronoFormat, liveSources == 1)
            views.setContentDescription(
                R.id.widget_today,
                if (state.dailyGoalMin > 0) {
                    context.getString(
                        R.string.widget_today_goal_content_description,
                        hm(todaySecs),
                        hm(state.dailyGoalMin * 60.0)
                    )
                } else {
                    context.getString(R.string.widget_today_content_description, hm(todaySecs))
                }
            )

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            views.setOnClickPendingIntent(
                R.id.widget_root,
                PendingIntent.getActivity(context, 0, intent, flags)
            )
            return views
        }

        /** 与 ViewModel.todaySeconds 对齐:已结算 daily + 今日范围内的在途时长。 */
        private fun todaySeconds(state: AppState, now: Long): Double {
            val base = state.daily[TimeUtil.todayKey(now)] ?: 0.0
            return base + liveExtra(state, now)
        }

        private fun liveExtra(state: AppState, now: Long): Double {
            val today = TimeUtil.todayKey(now)
            var extra = 0.0
            for (sub in state.subjects) for (it in sub.items) {
                val rs = it.runningSince
                if (rs != null) {
                    extra += TimeUtil.secondsOnLocalDay(rs, now, today)
                }
            }
            val pomo = state.pomo ?: return extra
            if (pomo.phase != "focus" && pomo.phase != "overtime") return extra
            val capped = when {
                pomo.pausedAt != null -> pomo.pausedAt
                pomo.phase == "overtime" -> now
                else -> min(now, pomo.endsAt)
            }
            // 新状态累计已完成段 + 当前活动段;旧状态由 helper 回退为连续区间。
            for (segment in pomo.focusIntervalsUntil(capped)) {
                extra += TimeUtil.secondsOnLocalDay(
                    segment.startAt,
                    segment.endAt,
                    today
                )
            }
            return extra
        }

        /** 当前真正按秒增长的来源数。正常交互为 0 或 1;返回值也用于保护异常并发态。 */
        private fun liveSourceCount(state: AppState, now: Long): Int {
            var count = state.subjects.sumOf { subject ->
                subject.items.count { item -> item.runningSince?.let { it <= now } == true }
            }
            val pomo = state.pomo
            if (pomo != null && pomo.pausedAt == null) {
                val activeStart = pomo.activeFocusStartedAt
                    ?: if (pomo.focusSegments == null) pomo.startAt else null
                val growing = when (pomo.phase) {
                    "focus" -> activeStart != null && now in activeStart until pomo.endsAt
                    "overtime" -> activeStart != null && now >= activeStart
                    else -> false
                }
                if (growing) count++
            }
            return count
        }
    }
}
