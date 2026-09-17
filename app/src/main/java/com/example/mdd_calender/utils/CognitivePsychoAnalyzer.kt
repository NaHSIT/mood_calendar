package com.example.mdd_calender.utils

import com.example.mdd_calender.data.MoodRecord
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.abs

enum class RiskLevel {
    LOW, MEDIUM, HIGH, CRISIS
}

data class PsychoInsightReport(
    val riskLevel: RiskLevel,
    val summary: String,
    val comfortMessage: String?,
    val identifiedDistortions: List<String>,
    val suspectedDisorders: List<String>,
    val advice: String,
    val medicalReferral: String?
)

object CognitivePsychoAnalyzer {

    // 1. Crisis Intervention Lexicon
    private val crisisWords = listOf(
        "不想活", "想死", "自杀", "结束生命", "解脱", "告别", "活不下去",
        "一死了之", "活着没意思", "死掉算了", "没有意义了", "彻底消失"
    )

    // 2. MDD Core Symptoms (Depression)
    private val severeDepressionWords = listOf(
        "绝望", "毫无价值", "累", "痛苦", "没用", "废物", "行尸走肉",
        "没胃口", "吃不下", "睡不着", "失眠", "想哭", "空虚", "没人爱我"
    )

    // 3. PPD (Postpartum Depression) Lexicon
    private val ppdWords = listOf(
        "宝宝", "喂奶", "不是个好妈妈", "愧疚", "月子", "生完孩子", 
        "不配当妈", "哭闹", "奶水", "带娃", "熬夜"
    )

    // 4. PTSD (Post-Traumatic Stress Disorder) Lexicon
    private val ptsdWords = listOf(
        "闪回", "噩梦", "创伤", "重温", "那件事", "挥之不去", 
        "战争", "血", "尖叫", "刺眼", "像做梦一样", "不是我", "不想提"
    )

    // 5. Bipolar Mania Lexicon
    private val bipolarManiaWords = listOf(
        "狂躁", "不用睡", "几天没睡", "精力充沛", "思维奔逸", "情绪过山车", 
        "无所不能", "狂喜", "停不下来"
    )

    // 6. GAD (Generalized Anxiety)
    private val severeAnxietyWords = listOf(
        "心慌", "害怕", "发抖", "喘不过气", "快死", "极度紧张", "要疯了",
        "失控", "崩溃", "窒息", "担惊受怕", "提心吊胆", "恐慌"
    )

    // 7. CBT Distortions
    private val allOrNothingWords = listOf("总是", "绝不", "永远", "全完了", "一点都不", "根本不", "彻底")
    private val catastrophizingWords = listOf("太可怕了", "完了", "彻底没救", "无法忍受", "天塌了")
    private val overgeneralizationWords = listOf("每个人都", "没人", "每次都", "到处")
    
    // First person pronouns for self-absorption detection
    private val firstPersonPronouns = listOf("我", "我的", "自己")

    private val negativeMoodTypes = listOf("难过", "生气")

    fun analyze(records: List<MoodRecord>): PsychoInsightReport {
        if (records.isEmpty()) {
            return PsychoInsightReport(
                riskLevel = RiskLevel.LOW,
                summary = "数据不足",
                comfortMessage = null,
                identifiedDistortions = emptyList(),
                suspectedDisorders = emptyList(),
                advice = "暂无足够数据，请多记录您的心情与感受以获取深度心理分析。",
                medicalReferral = null
            )
        }

        var isCrisis = false
        var depressionScore = 0
        var anxietyScore = 0
        var ppdScore = 0
        var ptsdScore = 0
        var maniaScore = 0
        
        var totalFirstPersonCount = 0
        var totalWordCount = 0

        val foundDistortions = mutableSetOf<String>()
        val suspectedDisorders = mutableSetOf<String>()
        
        var maxConsecutiveNegativeDays = 0
        var recentHasImage = false
        var insomniaCount = 0

        val groupedRecords = records.groupBy { LocalDate.parse(it.date, DateTimeFormatter.ISO_LOCAL_DATE) }
        val sortedDates = groupedRecords.keys.sorted()
        var previousDate: LocalDate? = null
        var consecutiveNegativeDays = 0
        
        // Track word count over time for Phase Shift (Bipolar) & Anhedonia
        val dailyWordCounts = mutableMapOf<LocalDate, Int>()

        for (date in sortedDates) {
            val dailyRecords = groupedRecords[date]!!
            var dailyWordCount = 0
            
            for (record in dailyRecords) {
                val textToAnalyze = "${record.note.orEmpty()} ${record.content.orEmpty()}".lowercase()
                dailyWordCount += textToAnalyze.length
                totalWordCount += textToAnalyze.length

                if (!record.imageUris.isNullOrEmpty()) {
                    recentHasImage = true
                }

                // Chronobiological Marker: Insomnia / Early Morning Awakening (3 AM - 5 AM)
                if (record.time.isNotEmpty()) {
                    try {
                        val time = LocalTime.parse(record.time)
                        if (time.hour in 3..5 && record.moodType in negativeMoodTypes) {
                            insomniaCount++
                            depressionScore += 2 // Weighted higher
                        }
                    } catch (e: Exception) {
                        // ignore parsing error
                    }
                }

                // Lexicon matching
                if (crisisWords.any { textToAnalyze.contains(it) }) isCrisis = true
                if (severeDepressionWords.any { textToAnalyze.contains(it) }) depressionScore += 1
                if (severeAnxietyWords.any { textToAnalyze.contains(it) }) anxietyScore += 1
                if (ppdWords.any { textToAnalyze.contains(it) }) ppdScore += 1
                if (ptsdWords.any { textToAnalyze.contains(it) }) ptsdScore += 1
                if (bipolarManiaWords.any { textToAnalyze.contains(it) }) maniaScore += 2 // Weighted higher

                if (allOrNothingWords.any { textToAnalyze.contains(it) }) foundDistortions.add("全或无思维 (两极化)")
                if (catastrophizingWords.any { textToAnalyze.contains(it) }) foundDistortions.add("灾难化思维")
                if (overgeneralizationWords.any { textToAnalyze.contains(it) }) foundDistortions.add("过度概括")
                
                // NLP Marker: First person pronoun density
                firstPersonPronouns.forEach { pronoun ->
                    totalFirstPersonCount += textToAnalyze.windowed(pronoun.length).count { it == pronoun }
                }
            }
            
            dailyWordCounts[date] = dailyWordCount

            // Behavioral Trend Tracking
            val isNegativeDay = dailyRecords.any { it.moodType in negativeMoodTypes }
            if (previousDate != null && ChronoUnit.DAYS.between(previousDate, date) == 1L) {
                if (isNegativeDay) {
                    consecutiveNegativeDays++
                    if (consecutiveNegativeDays > maxConsecutiveNegativeDays) maxConsecutiveNegativeDays = consecutiveNegativeDays
                } else {
                    consecutiveNegativeDays = 0
                }
            } else {
                consecutiveNegativeDays = if (isNegativeDay) 1 else 0
                if (maxConsecutiveNegativeDays == 0) maxConsecutiveNegativeDays = consecutiveNegativeDays
            }
            previousDate = date
        }

        // --- Advanced Diagnostics & Pattern Recognition ---
        
        // 1. MDD (Major Depressive Disorder)
        val fpDensity = if (totalWordCount > 0) totalFirstPersonCount.toFloat() / totalWordCount else 0f
        if (depressionScore >= 4 || (depressionScore >= 2 && fpDensity > 0.05f) || maxConsecutiveNegativeDays >= 7 || insomniaCount >= 3) {
            suspectedDisorders.add("重度抑郁症 (MDD) 倾向")
        }
        
        // 2. GAD (Generalized Anxiety Disorder)
        if (anxietyScore >= 4) {
            suspectedDisorders.add("广泛性焦虑障碍 (GAD) 倾向")
        }
        
        // 3. PPD (Postpartum Depression)
        if (ppdScore >= 3 && (depressionScore >= 2 || insomniaCount >= 1)) {
            suspectedDisorders.add("产后抑郁症 (PPD) 倾向")
        }
        
        // 4. PTSD (Post-Traumatic Stress Disorder)
        if (ptsdScore >= 3 && anxietyScore >= 1) {
            suspectedDisorders.add("创伤后应激障碍 (PTSD) 倾向")
        }
        
        // 5. Bipolar Disorder (Phase Shift Detection)
        // Check for sharp spike in word count alongside mania words
        var hasPhaseShift = false
        val datesList = dailyWordCounts.keys.toList()
        for (i in 1 until datesList.size) {
            val prevWords = dailyWordCounts[datesList[i-1]] ?: 0
            val currWords = dailyWordCounts[datesList[i]] ?: 0
            if (prevWords < 50 && currWords > 300) {
                hasPhaseShift = true // Sudden burst of energy
            }
        }
        if (maniaScore >= 3 || (hasPhaseShift && maniaScore >= 1 && depressionScore >= 2)) {
            suspectedDisorders.add("双相情感障碍 (Bipolar) 倾向")
        }

        // Evaluate Risk Level
        val riskLevel = when {
            isCrisis -> RiskLevel.CRISIS
            suspectedDisorders.isNotEmpty() -> RiskLevel.HIGH
            depressionScore in 1..3 || anxietyScore in 1..3 || foundDistortions.isNotEmpty() || maxConsecutiveNegativeDays >= 3 -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }

        // Generate Empathy/Comfort Message
        var comfortMessage = when (riskLevel) {
            RiskLevel.CRISIS -> "我能看到你正身处深渊，写下这些文字一定很不容易。我在这里陪着你，你不必一个人默默承受这一切痛苦。"
            RiskLevel.HIGH -> "看着你这几天写下的情绪，我感到很心疼。现在的你就像背负着千斤重担在逆风行走，允许自己停下来休息一下好吗？"
            RiskLevel.MEDIUM -> "最近这段时间似乎有些波折，情绪就像天气一样阴晴不定。感到累或者沮丧是再正常不过的，抱抱你，你已经做得很好了。"
            RiskLevel.LOW -> null
        }
        
        if (recentHasImage && (riskLevel == RiskLevel.HIGH || riskLevel == RiskLevel.MEDIUM)) {
            val imageEmpathy = " 尽管文字里充满了疲惫，但我注意到你依然上传了照片。去记录生活本身，就是一种非常勇敢的自我连接，这本身就是巨大的力量。"
            comfortMessage = (comfortMessage ?: "") + imageEmpathy
        }

        // Generate Clinical Advice
        val advice = when {
            isCrisis -> "请先深呼吸。当痛苦大到难以承受时，这通常是一种疾病状态在影响你的大脑，这不是你的错。"
            suspectedDisorders.contains("创伤后应激障碍 (PTSD) 倾向") -> "创伤的记忆有时会像警报一样不受控制地被触发（闪回）。在心理学上，这代表大脑的保护机制过度激活。尝试使用“着陆技术（Grounding）”：感受双脚踩在地上的感觉，或者握住一块冰块，把意识拉回此时此地。"
            suspectedDisorders.contains("双相情感障碍 (Bipolar) 倾向") -> "情绪的剧烈起伏和思维的极度跳跃可能会让人感到失控。此时最重要的是保持规律的作息，不要在深夜做重大决定。"
            suspectedDisorders.contains("产后抑郁症 (PPD) 倾向") -> "成为母亲并不意味着必须完美。荷尔蒙的剧烈变化和极度的疲惫会放大这种内疚感。请把‘照顾宝宝’的一部分责任分担出去，你需要且值得被照顾。"
            suspectedDisorders.contains("重度抑郁症 (MDD) 倾向") -> "持续的强烈情绪可能会耗尽能量。心理学研究表明，当你感到被压垮时，缩小关注点，只做当下能让你好受一点的最小动作（比如喝杯温水）。"
            riskLevel == RiskLevel.MEDIUM -> {
                if (foundDistortions.isNotEmpty()) {
                    "我注意到你最近的想法受到了一些‘认知滤镜’的影响，比如[${foundDistortions.first()}]。在心理治疗中，我们称之为‘认知扭曲’。试着用温柔的方式反问自己：‘事情真的100%是这样吗？’"
                } else {
                    "接纳自己目前的状态。与其和负面情绪对抗，不如试着与它们共存。"
                }
            }
            else -> "你的心理弹性极佳！当前的认知模式非常健康。情绪日记是维持你内稳态的绝佳帮手。"
        }

        // Generate Medical Referral
        val medicalReferral = if (riskLevel == RiskLevel.CRISIS) {
            "【紧急预警】请立即向精神心理科医生寻求帮助。这是一次求救信号，去医院不是懦弱，而是最勇敢自救的开始。\n\n全国希望24小时热线：400-161-9995\n危机干预生命线：010-82951332"
        } else if (suspectedDisorders.isNotEmpty()) {
            var referral = "【就医建议】系统捕捉到了某些临床综合症的自然语言特征，强烈建议您前往正规医院（三甲医院精神心理科）进行确诊：\n"
            if (suspectedDisorders.contains("双相情感障碍 (Bipolar) 倾向")) {
                referral += "- 双相情感障碍需要精神科医师的专业药物评估（如心境稳定剂），单纯的心理咨询可能不够。\n"
            }
            if (suspectedDisorders.contains("创伤后应激障碍 (PTSD) 倾向")) {
                referral += "- 对于PTSD，可以向临床医生咨询是否适合接受 EMDR（眼动脱敏与再加工）或 TF-CBT 治疗。\n"
            }
            referral += "\n(免责声明：本分析基于文本模型，不能替代专业医师的诊断)"
            referral
        } else null

        val summary = when (riskLevel) {
            RiskLevel.CRISIS -> "危机预警：检测到极度绝望或伤害倾向"
            RiskLevel.HIGH -> "高危状态：触发临床心理疾病初筛警报"
            RiskLevel.MEDIUM -> "中度预警：存在情绪波动与认知疲劳"
            RiskLevel.LOW -> "状态良好：情绪稳定，认知模式健康"
        }

        return PsychoInsightReport(
            riskLevel = riskLevel,
            summary = summary,
            comfortMessage = comfortMessage,
            identifiedDistortions = foundDistortions.toList(),
            suspectedDisorders = suspectedDisorders.toList(),
            advice = advice,
            medicalReferral = medicalReferral
        )
    }
}
