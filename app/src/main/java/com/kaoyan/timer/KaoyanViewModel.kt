package com.kaoyan.timer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kaoyan.timer.audio.AudioEngine
import com.kaoyan.timer.data.Store
import com.kaoyan.timer.model.AppState
import com.kaoyan.timer.model.Item
import com.kaoyan.timer.model.Pomo
import com.kaoyan.timer.model.Subject
import com.kaoyan.timer.model.Subtask
import com.kaoyan.timer.model.Templates
import com.kaoyan.timer.service.PomodoroService
import com.kaoyan.timer.util.TimeUtil
import com.kaoyan.timer.util.mmss
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.ceil
import kotlin.math.roundToInt

class KaoyanViewModel(app: Application) : AndroidViewModel(app) {

    private val store = Store(app.applicationContext)
    val audio = AudioEngine(app.applicationContext)

    private val _state = MutableStateFlow(store.load())
    val state: StateFlow<AppState> = _state.asStateFlow()

    private val _now = MutableStateFlow(System.currentTimeMillis())
    val now: StateFlow<Long> = _now.asStateFlow()

    // itemId -> Item 的索引,结构变化后重建
    private var itemById: Map<String, Item> = buildIndex(_state.value)

    // 前台服务是否已开,避免每秒重复 startForegroundService
    private var pomoServiceOn = false

    init {
        reconcile()
        viewModelScope.launch {
            while (true) {
                try {
                    val n = System.currentTimeMillis()
                    _now.value = n
                    tickPomo(n)
                    syncPomoNotification(n)
                } catch (_: Throwable) {
                    // 单次 tick 异常不能打断整个心跳:否则 _now 永久冻结、所有计时(含超时正计时)卡死且重启才恢复
                }
                delay(1000)
            }
        }
    }

    /** 番茄在跑就开/更新前台服务通知,停了就关。计时仍由本 ViewModel 驱动,服务只保活。 */
    private fun syncPomoNotification(now: Long) {
        val app = getApplication<Application>()
        val p = _state.value.pomo
        if (p == null) {
            if (pomoServiceOn) {
                PomodoroService.stop(app)
                pomoServiceOn = false
            }
            return
        }
        val item = findItem(_state.value, p.itemId)
        val title = if (item != null) "${subjectNameOf(_state.value, p.itemId)} · ${item.name}" else "番茄专注"
        val phase = when {
            p.pausedAt != null -> "已暂停"
            p.phase == "overtime" -> "超时中"
            p.phase == "break" -> "休息中"
            else -> "专注中"
        }
        val chain = if (p.subtaskId != null && item != null) {
            val idx = item.subtasks.indexOfFirst { it.id == p.subtaskId }
            if (idx >= 0) " · 第 ${idx + 1}/${item.subtasks.size}" else ""
        } else ""
        // 运行态:让系统 chronometer 渲染计时(息屏/Doze 也不冻结);暂停态:静态文案冻结在当前值
        val paused = p.pausedAt != null
        val countDown = p.phase != "overtime"
        val text = if (paused) {
            val frozen = if (p.phase == "overtime") "+" + mmss(pomoOvertimeSec(now)) else mmss(pomoRemainSec(now))
            "$phase $frozen$chain"
        } else {
            "$phase$chain"
        }
        if (!pomoServiceOn) {
            PomodoroService.start(app, title, text, p.endsAt, chrono = !paused, countDown = countDown)
            pomoServiceOn = true
        } else {
            PomodoroService.update(app, title, text, p.endsAt, chrono = !paused, countDown = countDown)
        }
    }

    // ---------------------------------------------------------------
    // 内部工具
    // ---------------------------------------------------------------
    private fun buildIndex(s: AppState): Map<String, Item> {
        val m = HashMap<String, Item>()
        for (sub in s.subjects) for (it in sub.items) m[it.id] = it
        return m
    }

    /** 任何修改后:重建索引、发布新引用、持久化。 */
    private fun publish(s: AppState) {
        itemById = buildIndex(s)
        _state.value = s
        store.save(s)
    }

    /** 复制当前 state(深拷贝 subjects/items 以便修改可变字段后发布新引用)。 */
    private fun copyState(src: AppState): AppState {
        val newSubjects = src.subjects.map { sub ->
            Subject(
                name = sub.name,
                items = sub.items.map { it ->
                    Item(
                        id = it.id,
                        name = it.name,
                        seconds = it.seconds,
                        runningSince = it.runningSince,
                        subtasks = it.subtasks.map { st -> st.copy() } // 深拷贝:Subtask.done 可变,必须新实例
                    )
                }
            )
        }
        return AppState(
            examDate = src.examDate,
            startDate = src.startDate,
            subjects = newSubjects,
            daily = HashMap(src.daily),
            dailySub = HashMap(src.dailySub.mapValues { HashMap(it.value) }),
            pomo = src.pomo?.let { Pomo(it.itemId, it.phase, it.startAt, it.endsAt, it.pausedAt, it.subtaskId) },
            focusMin = src.focusMin,
            breakMin = src.breakMin,
            template = src.template,
            sel = HashMap(src.sel),
            lastPomoItemId = src.lastPomoItemId
        )
    }

    /** 结算秒数:同时记到当日总时长 daily 与当日该科 dailySub。subject 为空则只记总量。 */
    private fun addDaily(s: AppState, key: String, secs: Double, subject: String) {
        val cur = s.daily[key] ?: 0.0
        s.daily[key] = (cur + secs).coerceAtLeast(0.0)
        if (subject.isNotEmpty()) {
            val m = s.dailySub.getOrPut(key) { mutableMapOf() }
            m[subject] = ((m[subject] ?: 0.0) + secs).coerceAtLeast(0.0)
        }
    }

    /** 找 itemId 所属科目名,用于按科记账。 */
    private fun subjectNameOf(s: AppState, itemId: String): String =
        s.subjects.firstOrNull { sub -> sub.items.any { it.id == itemId } }?.name ?: ""

    private fun findItem(s: AppState, itemId: String): Item? =
        s.subjects.flatMap { it.items }.firstOrNull { it.id == itemId }

    /** 拆解链里下一个未完成的小任务(按列表顺序)。 */
    private fun nextUndoneSubtask(item: Item): Subtask? =
        item.subtasks.firstOrNull { !it.done }

    /** 开番茄前调用:把所有在跑的子项秒表结算并清零,实现与番茄互斥,避免同段双记。 */
    private fun settleAllRunning(s: AppState, now: Long) {
        for (sub in s.subjects) for (it in sub.items) {
            val rs = it.runningSince ?: continue
            val elapsed = (now - rs) / 1000.0
            if (elapsed > 0) {
                it.seconds += elapsed
                addDaily(s, TimeUtil.todayKey(now), elapsed, sub.name)
            }
            it.runningSince = null
        }
    }

    // ---------------------------------------------------------------
    // 动作
    // ---------------------------------------------------------------
    fun applyTemplate(key: String) {
        val s = copyState(_state.value)
        val tpl = Templates.all[key] ?: return
        @Suppress("UNUSED_VARIABLE")
        val unused = tpl
        val newState = AppState(
            examDate = s.examDate,
            startDate = s.startDate,
            subjects = Templates.build(key),
            daily = HashMap(s.daily), // 保留 daily
            dailySub = HashMap(s.dailySub.mapValues { HashMap(it.value) }), // 保留按科历史
            pomo = null,
            focusMin = s.focusMin,
            breakMin = s.breakMin,
            template = key,
            sel = HashMap(),
            lastPomoItemId = null // 换模板后子项 id 全变,重置番茄选择
        )
        publish(newState)
    }

    fun toggleItem(id: String) {
        val s = copyState(_state.value)
        val now = System.currentTimeMillis()
        val item = s.subjects.flatMap { it.items }.firstOrNull { it.id == id } ?: return
        val rs = item.runningSince
        if (rs == null) {
            // 开始
            item.runningSince = now
        } else {
            // 停止:结算
            val elapsed = (now - rs) / 1000.0
            if (elapsed > 0) {
                item.seconds += elapsed
                addDaily(s, TimeUtil.todayKey(now), elapsed, subjectNameOf(s, id))
            }
            item.runningSince = null
        }
        publish(s)
    }

    fun manualAdd(id: String, minutes: Double) {
        val s = copyState(_state.value)
        val now = System.currentTimeMillis()
        val item = s.subjects.flatMap { it.items }.firstOrNull { it.id == id } ?: return
        val secs = minutes * 60.0
        item.seconds = (item.seconds + secs).coerceAtLeast(0.0)
        addDaily(s, TimeUtil.todayKey(now), secs, subjectNameOf(s, id))
        publish(s)
    }

    /** 保存某子项的小任务清单(拆解面板编辑后调用),不启动番茄。运行态不应调用。 */
    fun saveSubtasks(itemId: String, subtasks: List<Subtask>) {
        val s = copyState(_state.value)
        val item = findItem(s, itemId) ?: return
        item.subtasks = subtasks.map { it.copy() }
        publish(s)
    }

    fun selectSubItem(subjectName: String, itemId: String) {
        val s = copyState(_state.value)
        val now = System.currentTimeMillis()
        val subject = s.subjects.firstOrNull { it.name == subjectName }
        if (subject != null) {
            // 先把同科目所有在跑 item 结算暂停
            for (it in subject.items) {
                val rs = it.runningSince
                if (rs != null) {
                    val elapsed = (now - rs) / 1000.0
                    if (elapsed > 0) {
                        it.seconds += elapsed
                        addDaily(s, TimeUtil.todayKey(now), elapsed, subject.name)
                    }
                    it.runningSince = null
                }
            }
        }
        s.sel[subjectName] = itemId
        publish(s)
    }

    /** 记住番茄钟当前选中的子项(仅持久化,不结算任何在跑秒表,避免 selectSubItem 的副作用)。 */
    fun selectPomoItem(itemId: String) {
        val s = copyState(_state.value)
        s.lastPomoItemId = itemId
        publish(s)
    }

    fun startPomo(itemId: String) {
        val s = copyState(_state.value)
        val now = System.currentTimeMillis()
        settleAllRunning(s, now) // 与子项秒表互斥
        val focusMs = s.focusMin.toLong() * 60_000L
        s.pomo = Pomo(
            itemId = itemId,
            phase = "focus",
            startAt = now,
            endsAt = now + focusMs
        )
        publish(s)
    }

    /**
     * 开始一条拆解链:跑该 item 第一个未完成的小任务(按它自己的 estMin 当 focus 时长)。
     * 全部已完成则把整条链重置后从头开始。与子项秒表互斥。
     */
    fun startSubtaskChain(itemId: String) {
        val s = copyState(_state.value)
        val now = System.currentTimeMillis()
        settleAllRunning(s, now)
        val item = findItem(s, itemId) ?: return
        if (item.subtasks.isEmpty()) return
        // 防御:一次性流程正常不会留全完成的链;万一有(旧数据/边角)就重置重跑,避免空转死角
        if (item.subtasks.all { it.done }) item.subtasks.forEach { it.done = false }
        val st = nextUndoneSubtask(item) ?: return
        val focusMs = st.estMin.toLong() * 60_000L
        s.pomo = Pomo(
            itemId = item.id,
            phase = "focus",
            startAt = now,
            endsAt = now + focusMs,
            subtaskId = st.id
        )
        publish(s)
    }

    /**
     * 清单条手动打勾。若正专注这一段=立即结算已流逝并接力下一个(与到期 tick 幂等);
     * 否则仅标记完成,接力时自动跳过。
     */
    fun markSubtaskDone(itemId: String, subtaskId: String) {
        val s = copyState(_state.value)
        val now = System.currentTimeMillis()
        val item = findItem(s, itemId) ?: return
        val st = item.subtasks.firstOrNull { it.id == subtaskId } ?: return
        val p = s.pomo
        if (p != null && p.subtaskId == subtaskId && (p.phase == "focus" || p.phase == "overtime")) {
            // 结算已专注(含超时):focus 封顶到 endsAt;overtime 算到 now
            val end = p.pausedAt ?: if (p.phase == "overtime") now else minOf(now, p.endsAt)
            val elapsed = (end - p.startAt) / 1000.0
            if (elapsed > 0) {
                item.seconds += elapsed
                addDaily(s, TimeUtil.todayKey(end), elapsed, subjectNameOf(s, itemId))
            }
            st.done = true
            val next = nextUndoneSubtask(item)
            if (next != null) {
                // 完成本段 → 进休息(专注时长 20%),休息完自动接力下一个
                val breakMin = (elapsed / 60.0 * 0.2).roundToInt().coerceIn(3, 20)
                s.pomo = Pomo(itemId, "break", now, now + breakMin.toLong() * 60_000L, subtaskId = subtaskId)
            } else {
                item.subtasks = emptyList() // 一次性:最后一个完成即清空整条链
                s.pomo = null
            }
        } else {
            st.done = true
        }
        publish(s)
    }

    fun pausePomo() {
        val cur = _state.value.pomo ?: return
        if (cur.pausedAt != null) return
        val s = copyState(_state.value)
        val now = System.currentTimeMillis()
        // 超时正计时无 endsAt 上限,冻结在 now;focus/break 封顶到 endsAt
        val freeze = if (cur.phase == "overtime") now else minOf(now, cur.endsAt)
        s.pomo = s.pomo?.copy(pausedAt = freeze)
        publish(s)
    }

    fun resumePomo() {
        val cur = _state.value.pomo ?: return
        val pausedAt = cur.pausedAt ?: return
        val s = copyState(_state.value)
        val now = System.currentTimeMillis()
        val delta = now - pausedAt
        s.pomo = s.pomo?.copy(
            startAt = cur.startAt + delta,
            endsAt = cur.endsAt + delta,
            pausedAt = null
        )
        publish(s)
    }

    fun stopPomo() {
        val s = copyState(_state.value)
        val p = s.pomo
        if (p != null && (p.phase == "focus" || p.phase == "overtime")) {
            // 中途停止也结算已专注时间(含超时;暂停时取冻结点),避免白干
            val now = System.currentTimeMillis()
            val end = p.pausedAt ?: if (p.phase == "overtime") now else minOf(now, p.endsAt)
            val elapsed = (end - p.startAt) / 1000.0
            if (elapsed > 0) {
                val item = findItem(s, p.itemId)
                if (item != null) {
                    item.seconds += elapsed
                    addDaily(s, TimeUtil.todayKey(end), elapsed, subjectNameOf(s, p.itemId))
                }
            }
        }
        // 停止=正常停止:保留拆解链(含已完成进度),下次可继续;只有整条链跑完才清空
        s.pomo = null
        publish(s)
    }

    fun setExamDate(s: String) {
        val st = copyState(_state.value)
        st.examDate = s
        publish(st)
    }

    fun setStartDate(s: String) {
        val st = copyState(_state.value)
        st.startDate = s
        publish(st)
    }

    fun setFocusMin(v: Int) {
        val st = copyState(_state.value)
        st.focusMin = v.coerceAtLeast(1)
        publish(st)
    }

    fun setBreakMin(v: Int) {
        val st = copyState(_state.value)
        st.breakMin = v.coerceAtLeast(1)
        publish(st)
    }

    // ---------------------------------------------------------------
    // tickPomo / reconcile
    // ---------------------------------------------------------------
    private fun tickPomo(now: Long) {
        val pomo = _state.value.pomo ?: return
        if (pomo.pausedAt != null) return // 暂停中不推进,否则墙钟越过 endsAt 会误结算
        if (pomo.phase == "overtime") return // 超时正计时:不自动结算/推进,等用户点完成
        if (now < pomo.endsAt) return

        val s = copyState(_state.value)
        val p = s.pomo ?: return
        if (p.phase == "focus") {
            if (p.subtaskId != null) {
                // 拆解链:预估时间到 → 转超时正计时(不结算、不标完成),响铃提示
                audio.beep()
                s.pomo = Pomo(
                    itemId = p.itemId,
                    phase = "overtime",
                    startAt = p.startAt, // 保留原起点,完成时按 now-startAt 结算总时长
                    endsAt = p.endsAt,   // 预估到点时刻,用于显示超时 +mm:ss
                    subtaskId = p.subtaskId
                )
            } else {
                // 普通番茄:结算 focus → 休息
                val item = findItem(s, p.itemId)
                val focusSecs = (p.endsAt - p.startAt) / 1000.0
                if (item != null && focusSecs > 0) {
                    item.seconds += focusSecs
                    addDaily(s, TimeUtil.todayKey(p.endsAt), focusSecs, subjectNameOf(s, p.itemId))
                }
                audio.beep()
                val breakMs = s.breakMin.toLong() * 60_000L
                s.pomo = Pomo(p.itemId, "break", p.endsAt, p.endsAt + breakMs)
            }
        } else {
            // break 结束 → 自动接力下一个未完成小任务
            audio.beep()
            val item = if (p.subtaskId != null) findItem(s, p.itemId) else null
            val next = item?.let { nextUndoneSubtask(it) }
            if (next != null) {
                s.pomo = Pomo(
                    itemId = p.itemId,
                    phase = "focus",
                    startAt = now,
                    endsAt = now + next.estMin.toLong() * 60_000L,
                    subtaskId = next.id
                )
            } else {
                // 一次性:整条链跑完即清空小任务,下次需重新拆解
                if (item != null) item.subtasks = emptyList()
                s.pomo = null
            }
        }
        publish(s)
    }

    /** init 里:若 pomo 且 now>=endsAt,focus 补记一次后清空。 */
    private fun reconcile() {
        val pomo = _state.value.pomo ?: return
        if (pomo.pausedAt != null) return // 重启后保持暂停态,不结算
        val now = System.currentTimeMillis()
        if (pomo.phase != "overtime" && now < pomo.endsAt) return

        val s = copyState(_state.value)
        val p = s.pomo ?: return
        if (p.phase == "focus" || p.phase == "overtime") {
            // 安全降级:overtime 无法知道实际跑多久,只补记到预估到点(endsAt);不标完成。
            val item = findItem(s, p.itemId)
            val focusSecs = (p.endsAt - p.startAt) / 1000.0
            if (item != null && focusSecs > 0) {
                item.seconds += focusSecs
                addDaily(s, TimeUtil.todayKey(p.endsAt), focusSecs, subjectNameOf(s, p.itemId))
            }
        }
        // 不重放后续链、不标完成、不清空拆解:链保留,重开后从清单条继续下一个未完成的小任务。
        s.pomo = null
        publish(s)
    }

    // ---------------------------------------------------------------
    // 计算
    // ---------------------------------------------------------------
    fun itemSeconds(it: Item, now: Long): Double {
        val rs = it.runningSince
        return it.seconds + if (rs != null) (now - rs) / 1000.0 else 0.0
    }

    fun subjectSeconds(s: Subject, now: Long): Double {
        var sum = 0.0
        for (it in s.items) sum += itemSeconds(it, now)
        return sum
    }

    /** 所有在跑 item 的在途秒数 + pomo focus 在途秒数。 */
    fun liveExtra(now: Long): Double {
        val s = _state.value
        var extra = 0.0
        for (sub in s.subjects) for (it in sub.items) {
            val rs = it.runningSince
            if (rs != null) extra += (now - rs) / 1000.0
        }
        val pomo = s.pomo
        if (pomo != null && pomo.phase == "focus") {
            val cappedNow = pomo.pausedAt ?: minOf(now, pomo.endsAt)
            val elapsed = (cappedNow - pomo.startAt) / 1000.0
            if (elapsed > 0) extra += elapsed
        }
        return extra
    }

    /** 某科当前在途秒数:该科在跑 item + (若 pomo focus 的 item 属于该科)番茄在途。 */
    private fun liveExtraForSubject(sub: Subject, now: Long): Double {
        var e = 0.0
        for (it in sub.items) {
            val rs = it.runningSince
            if (rs != null) e += (now - rs) / 1000.0
        }
        val pomo = _state.value.pomo
        if (pomo != null && pomo.phase == "focus" && sub.items.any { it.id == pomo.itemId }) {
            val cappedNow = pomo.pausedAt ?: minOf(now, pomo.endsAt)
            val elapsed = (cappedNow - pomo.startAt) / 1000.0
            if (elapsed > 0) e += elapsed
        }
        return e
    }

    data class SubjectSlice(val name: String, val secs: Double)

    /**
     * 各科占比饼图取数。range: "today" 今日 / "week" 近7天 / "all" 累计。
     * 已结算部分 today/week 取 dailySub,all 取 item.seconds 之和;再叠加该科今日在途。
     */
    fun subjectBreakdown(range: String, now: Long): List<SubjectSlice> {
        val s = _state.value
        val keys: List<String> = when (range) {
            "today" -> listOf(TimeUtil.todayKey(now))
            "week" -> (0..6).map { TimeUtil.dayKeyOffset(now, it) }
            else -> emptyList()
        }
        return s.subjects.map { sub ->
            val settled = if (range == "all") {
                sub.items.sumOf { it.seconds }
            } else {
                keys.sumOf { k -> s.dailySub[k]?.get(sub.name) ?: 0.0 }
            }
            SubjectSlice(sub.name, settled + liveExtraForSubject(sub, now))
        }
    }

    /** 番茄剩余秒数;暂停时以冻结点计,供两个 UI 复用。 */
    fun pomoRemainSec(now: Long): Long {
        val p = _state.value.pomo ?: return 0L
        val ref = p.pausedAt ?: now
        return ((p.endsAt - ref) / 1000L).coerceAtLeast(0L)
    }

    fun pomoIsOvertime(): Boolean = _state.value.pomo?.phase == "overtime"

    /** 超时正计时已超出的秒数(now − 预估到点);暂停时取冻结点。 */
    fun pomoOvertimeSec(now: Long): Long {
        val p = _state.value.pomo ?: return 0L
        if (p.phase != "overtime") return 0L
        val ref = p.pausedAt ?: now
        return ((ref - p.endsAt) / 1000L).coerceAtLeast(0L)
    }

    fun todaySeconds(now: Long): Double {
        val s = _state.value
        val base = s.daily[TimeUtil.todayKey(now)] ?: 0.0
        return base + liveExtra(now)
    }

    data class CountInfo(val days: Int, val hours: Int, val diffMs: Long)

    fun countdown(now: Long): CountInfo {
        val examMs = TimeUtil.dateToMillisLocalMidnight(_state.value.examDate)
        val diff = examMs - now
        val safeDiff = diff.coerceAtLeast(0L)
        val days = ceil(safeDiff.toDouble() / TimeUtil.DAY_MS).toInt()
        val hours = ceil(safeDiff.toDouble() / TimeUtil.HOUR_MS).toInt()
        return CountInfo(days = days, hours = hours, diffMs = diff)
    }

    fun progressPct(now: Long): Float {
        val s = _state.value
        val start = TimeUtil.dateToMillisLocalMidnight(s.startDate)
        val exam = TimeUtil.dateToMillisLocalMidnight(s.examDate)
        val total = (exam - start).toDouble()
        if (total <= 0.0) return 0f
        val done = (now - start).toDouble()
        val pct = done / total * 100.0
        return pct.coerceIn(0.0, 100.0).toFloat()
    }

    data class DayBar(val secs: Double, val dow: Int, val today: Boolean)

    /** 近7天(含今天);今天的 secs 含 liveExtra。 */
    fun last7(now: Long): List<DayBar> {
        val s = _state.value
        val todayK = TimeUtil.todayKey(now)
        val result = ArrayList<DayBar>(7)
        for (i in 6 downTo 0) {
            val key = TimeUtil.dayKeyOffset(now, i)
            var secs = s.daily[key] ?: 0.0
            val isToday = key == todayK
            if (isToday) secs += liveExtra(now)
            val cal = Calendar.getInstance()
            cal.timeInMillis = now - i.toLong() * TimeUtil.DAY_MS
            // Calendar.DAY_OF_WEEK: 周日=1..周六=7 -> 转成 0..6 (周日=0)
            val dow = cal.get(Calendar.DAY_OF_WEEK) - 1
            result.add(DayBar(secs = secs, dow = dow, today = isToday))
        }
        return result
    }

    // ---------------------------------------------------------------
    // 备份 / 多端同步:导出已结算快照 / 导入覆盖
    // ---------------------------------------------------------------
    /** 导出此刻的干净快照:把在途时长结算进 seconds/daily,清掉运行中状态与 pomo。 */
    fun exportJson(): String {
        val now = System.currentTimeMillis()
        val today = TimeUtil.todayKey(now)
        val s = copyState(_state.value)
        // 趁 running/pomo 还在,先把今日各科在途折进 dailySub(与 daily[today] 一致)
        val m = s.dailySub.getOrPut(today) { mutableMapOf() }
        for (sub in _state.value.subjects) {
            val extra = liveExtraForSubject(sub, now)
            if (extra > 0) m[sub.name] = (m[sub.name] ?: 0.0) + extra
        }
        for (sub in s.subjects) for (it in sub.items) {
            it.seconds = itemSeconds(it, now)
            it.runningSince = null
        }
        s.daily[today] = todaySeconds(now)
        s.pomo = null
        return store.serialize(s)
    }

    /** 导入备份:覆盖本机数据。解析失败返回 false。 */
    fun importJson(text: String): Boolean {
        val imported = store.deserialize(text) ?: return false
        for (sub in imported.subjects) for (it in sub.items) it.runningSince = null
        imported.pomo = null
        if (imported.startDate.isBlank()) {
            imported.startDate = TimeUtil.todayKey(System.currentTimeMillis())
        }
        publish(imported)
        return true
    }

    override fun onCleared() {
        super.onCleared()
        audio.release()
        if (pomoServiceOn) {
            PomodoroService.stop(getApplication())
            pomoServiceOn = false
        }
    }
}
