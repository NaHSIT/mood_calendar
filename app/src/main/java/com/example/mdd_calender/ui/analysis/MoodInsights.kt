package com.example.mdd_calender.ui.analysis

import com.example.mdd_calender.data.MoodRecord
import com.example.mdd_calender.ui.components.MoodType
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.sqrt

data class DailyMoodPoint(val date: LocalDate, val score: Float, val count: Int)

data class ReflectionGuide(
    val focusDate: LocalDate?,
    val observation: String,
    val questions: List<String>,
)

data class RecordPattern(
    val latestStreak: Int,
    val longestStreak: Int,
    val variabilityLabel: String,
    val activePeriod: String,
    val highPointDate: LocalDate?,
    val lowPointDate: LocalDate?,
    val nextStep: String,
)

data class MoodInsight(
    val recordDays: Int,
    val dominantMood: MoodType?,
    val points: List<DailyMoodPoint>,
    val weeklySummary: String,
    val monthlySummary: String,
    val changeNotice: String,
    val reflectionPrompt: String,
    val reflectionGuide: ReflectionGuide,
    val recordPattern: RecordPattern,
)

fun buildMoodInsight(records: List<MoodRecord>, today: LocalDate = LocalDate.now()): MoodInsight {
    val valid = records.mapNotNull { record ->
        runCatching { LocalDate.parse(record.date) }.getOrNull()?.let { it to record }
    }
    val points = valid.groupBy({ it.first }, { it.second }).toSortedMap().map { (date, entries) ->
        DailyMoodPoint(date, entries.map { it.moodType.toMoodScore() }.average().toFloat(), entries.size)
    }
    val counts = records.groupingBy { it.moodType }.eachCount()
    val dominant = counts.maxByOrNull { it.value }?.key?.let(MoodType::fromLabel)
    val recent = points.filter { !it.date.isBefore(today.minusDays(6)) && !it.date.isAfter(today) }
    val previous = points.filter { it.date in today.minusDays(13)..today.minusDays(7) }
    val weekly = when {
        recent.isEmpty() -> "近 7 天还没有记录，先写下一次真实感受。"
        recent.size < 3 -> "近 7 天记录了 ${recent.size} 天；继续记录后，周趋势会更可靠。"
        else -> "近 7 天记录 ${recent.size} 天，平均情绪指数 ${recent.map { it.score }.average().format1()} / 5。"
    }
    val monthly = when {
        points.isEmpty() -> "本月暂无可分析记录。"
        points.size < 5 -> "本月已记录 ${points.size} 天，样本较少，暂不做趋势结论。"
        else -> "本月已记录 ${points.size} 天，共 ${records.size} 条；主导情绪为${dominant?.label ?: "未识别"}。"
    }
    val notice = if (recent.size >= 3 && previous.size >= 3) {
        val delta = recent.map { it.score }.average() - previous.map { it.score }.average()
        when {
            delta <= -0.8 -> "近 7 天较前一周明显回落。建议回看事件记录，必要时向可信任的人求助。"
            delta >= 0.8 -> "近 7 天较前一周有所回升，可以回顾哪些行动带来了帮助。"
            else -> "近两周整体变化平稳，继续记录有助于识别长期规律。"
        }
    } else "至少连续记录两个周期（每周 3 天以上）后，才能显示变化提醒。"
    val prompt = when (dominant) {
        MoodType.BAD, MoodType.AWFUL -> "哪些时刻最消耗你？有没有一件可减轻负担的小事？"
        MoodType.AMAZING, MoodType.GOOD -> "哪些人或行动支持了这份好心情？如何把它带到明天？"
        else -> if (points.isEmpty()) "今天发生了什么？它让你有什么感受？" else "回看最近的记录，哪一次情绪变化最值得继续了解？"
    }
    val focus = points.minByOrNull { it.score }
    val guide = ReflectionGuide(
        focusDate = focus?.date,
        observation = when {
            focus == null -> "还没有足够记录。可以先从今天发生的一件事开始。"
            focus.count > 1 -> "${focus.date.monthValue}月${focus.date.dayOfMonth}日记录了 ${focus.count} 次，日均情绪指数为 ${focus.score.toDouble().format1()} / 5。"
            else -> "${focus.date.monthValue}月${focus.date.dayOfMonth}日的情绪指数为 ${focus.score.toDouble().format1()} / 5，可以从这一天开始回看。"
        },
        questions = listOf(
            "当时发生了什么？请只描述可以观察到的事实。",
            "你脑中最先出现的想法是什么？它带来了什么感受？",
            "有哪些证据支持或不支持这个想法？能否换成更平衡的表达？",
        ),
    )
    val dates = points.map { it.date }
    val streaks = mutableListOf<Int>()
    dates.forEachIndexed { index, date ->
        if (index == 0 || date != dates[index - 1].plusDays(1)) {
            streaks += 1
        } else {
            streaks[streaks.lastIndex] += 1
        }
    }
    val average = points.map { it.score }.average()
    val deviation = if (points.size < 3) null else sqrt(points.map { (it.score - average) * (it.score - average) }.average())
    val periodCounts = valid.mapNotNull { (_, record) ->
        runCatching { LocalTime.parse(record.time) }.getOrNull()?.let { time ->
            when (time.hour) {
                in 5..10 -> "早晨"
                in 11..16 -> "午后"
                in 17..21 -> "晚间"
                else -> "深夜"
            }
        }
    }.groupingBy { it }.eachCount()
    val pattern = RecordPattern(
        latestStreak = streaks.lastOrNull() ?: 0,
        longestStreak = streaks.maxOrNull() ?: 0,
        variabilityLabel = when {
            deviation == null -> "数据不足"
            deviation < 0.55 -> "较平稳"
            deviation < 1.1 -> "有波动"
            else -> "波动较明显"
        },
        activePeriod = periodCounts.maxByOrNull { it.value }?.key ?: "暂无规律",
        highPointDate = points.maxByOrNull { it.score }?.date,
        lowPointDate = points.minByOrNull { it.score }?.date,
        nextStep = when {
            points.size < 3 -> "再记录几天，让趋势和波动判断更可靠。"
            deviation != null && deviation >= 1.1 -> "近期波动较明显，可回看高低点事件并完成一次量表自评。"
            dominant == MoodType.BAD || dominant == MoodType.AWFUL -> "低落记录较多，建议完成量表自评，并联系可信任的人获得支持。"
            else -> "继续保持记录，并回顾哪些行动帮助维持了当前状态。"
        },
    )
    return MoodInsight(points.size, dominant, points, weekly, monthly, notice, prompt, guide, pattern)
}

private fun String.toMoodScore(): Float = when (MoodType.fromLabel(this)) {
    MoodType.AMAZING -> 5f
    MoodType.GOOD -> 4f
    MoodType.MEH -> 3f
    MoodType.BAD -> 2f
    MoodType.AWFUL -> 1f
}

private fun Double.format1(): String = String.format(java.util.Locale.CHINA, "%.1f", this)
