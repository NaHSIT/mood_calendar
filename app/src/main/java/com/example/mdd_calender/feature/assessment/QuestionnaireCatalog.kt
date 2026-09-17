package com.example.mdd_calender.feature.assessment

data class QuestionnaireDescriptor(
    val type: AssessmentType,
    val displayName: String,
    val version: String,
    val recallPeriod: String,
    val items: List<String>,
    val responseOptions: List<ResponseOption>,
    val sourceUrl: String,
    val contentNotice: String,
) {
    /** Compatibility for the integration screen shipped before the reviewed item text. */
    @Deprecated("Use items; the values are no longer placeholders", ReplaceWith("items"))
    val itemPlaceholders: List<String> get() = items
}

data class ResponseOption(val score: Int, val label: String)

fun ScoreResult.Complete.toAssessmentRecord(
    assessmentId: String,
    studentId: String,
    instrumentVersion: String,
    answers: List<Int>,
    completedAtEpochMillis: Long,
): com.example.mdd_calender.domain.model.AssessmentRecord {
    require(answers.size == type.itemCount && answers.all { it in 0..3 })
    require(answers.sum() == total)
    return com.example.mdd_calender.domain.model.AssessmentRecord(
        assessmentId = assessmentId,
        studentId = studentId,
        type = type,
        instrumentVersion = instrumentVersion,
        answers = answers,
        totalScore = total,
        symptomBand = symptomBand,
        state = com.example.mdd_calender.domain.model.AssessmentState.COMPLETED,
        completedAtEpochMillis = completedAtEpochMillis,
        scoringVersion = AssessmentScorer.SCORING_VERSION,
    )
}

object QuestionnaireCatalog {
    val frequencyOptions = listOf(
        ResponseOption(0, "完全没有"),
        ResponseOption(1, "有几天"),
        ResponseOption(2, "超过一半的天数"),
        ResponseOption(3, "几乎每天"),
    )

    val phq9 = QuestionnaireDescriptor(
        type = AssessmentType.PHQ_9,
        displayName = "患者健康问卷 PHQ-9",
        version = "zh-CN-UW-2022",
        recallPeriod = "在过去两周内，您被以下问题困扰的频率如何？",
        items = listOf(
            "对做事没有兴趣或乐趣",
            "情绪低落、沮丧或绝望",
            "难以入睡或保持睡眠，或睡得太多",
            "感到疲倦或精力不足",
            "食欲不振或吃得过多",
            "对自己感觉不好——觉得自己很失败，或者让自己或家人失望了",
            "难以集中注意力，例如看报纸或看电视",
            "动作或说话慢到别人可能留意到；或者相反，烦躁或焦躁得比平时走动更多",
            "认为生不如死，或者想以某种方式伤害自己",
        ),
        responseOptions = frequencyOptions,
        sourceUrl = "https://dsf2.creatcom.washington.edu/DSF/PreviewPdf.ashx?fahDbaRbaYVOdaANxtX/hqAVN4WMqsRFMddRaCZP5D1hyL9etegm9VeDWPckWCSkra15ZbjYfyUgPgplBclAUPJRVAx/VbZw6QiYhGTrnErjedXk0VyyfF38oDSiQzTKSXpOa5G9s8tyoz1VJOgqn90YkPsL4RLWeMqcBiAD+EuIb9tb9nJJPQ=%3D",
        contentNotice = "本问卷用于症状筛查，不构成诊断。第 9 题非零时需要及时由专业人员开展安全评估。正式部署前仍需确认目标年龄、学校流程与转载授权。",
    )

    val gad7 = QuestionnaireDescriptor(
        type = AssessmentType.GAD_7,
        displayName = "广泛性焦虑量表 GAD-7",
        version = "zh-CN-mainland",
        recallPeriod = "在过去两周内，您被以下问题困扰的频率如何？",
        items = listOf(
            "感觉心神不安、焦虑或高度紧张",
            "不能停止或控制担心",
            "为各种各样的事情过度担心",
            "难以放松",
            "非常不安，以至于难以坐定",
            "变得容易恼火或急躁",
            "害怕发生可怕的事情",
        ),
        responseOptions = frequencyOptions,
        sourceUrl = "https://projectteachny.org/app/uploads/2022/07/GAD7_Simplified-Chinese-for-Mainland-China.pdf",
        contentNotice = "本问卷用于症状筛查，不构成诊断。简体中文版来源表单注明可无需许可复制、翻译、展示或分发；正式校园部署仍需确认目标年龄和专业处置流程。",
    )

    fun descriptor(type: AssessmentType): QuestionnaireDescriptor = when (type) {
        AssessmentType.PHQ_9 -> phq9
        AssessmentType.GAD_7 -> gad7
    }
}
