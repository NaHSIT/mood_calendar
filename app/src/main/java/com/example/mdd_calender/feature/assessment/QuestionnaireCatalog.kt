package com.example.mdd_calender.feature.assessment

data class QuestionnaireDescriptor(
    val type: AssessmentType,
    val displayName: String,
    val version: String,
    val recallPeriod: String,
    val itemPlaceholders: List<String>,
    val responseOptions: List<String>,
    val sourceUrl: String,
    val contentNotice: String,
)

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
    private val frequencyOptions = listOf("完全没有", "有几天", "一半以上天数", "几乎每天")

    val phq9 = QuestionnaireDescriptor(
        type = AssessmentType.PHQ_9,
        displayName = "PHQ-9 情绪自评",
        version = "app-zh-cn-v1",
        recallPeriod = "过去两周，你有多经常受到以下问题困扰？",
        itemPlaceholders = listOf(
            "做事时提不起劲或没有兴趣",
            "感到心情低落、沮丧或绝望",
            "入睡困难、睡不安稳，或睡眠过多",
            "感觉疲倦或没有活力",
            "食欲不振或吃得过多",
            "觉得自己很糟糕，或觉得自己很失败，或让自己或家人失望",
            "难以集中注意力，例如阅读或看视频时",
            "动作或说话慢到别人可能察觉；或相反，烦躁、坐立不安，比平常活动更多",
            "想到自己最好死去，或有伤害自己的念头",
        ),
        responseOptions = frequencyOptions,
        sourceUrl = "https://www.hiv.uw.edu/page/mental-health-screening/phq-9",
        contentNotice = "这是筛查自评，不是诊断。应用内简体中文表述依据英文原量表，正式使用前仍需本地专业审校与年龄适用性确认。",
    )

    val gad7 = QuestionnaireDescriptor(
        type = AssessmentType.GAD_7,
        displayName = "GAD-7 焦虑自评",
        version = "app-zh-cn-v1",
        recallPeriod = "过去两周，你有多经常受到以下问题困扰？",
        itemPlaceholders = listOf(
            "感到紧张、焦虑或急切",
            "无法停止或控制担忧",
            "对各种事情担忧过多",
            "很难放松下来",
            "坐立不安，难以安静地坐着",
            "容易烦恼或易怒",
            "感到似乎将有可怕的事情发生",
        ),
        responseOptions = frequencyOptions,
        sourceUrl = "https://www.hiv.uw.edu/page/mental-health-screening/gad-7",
        contentNotice = "这是筛查自评，不是诊断。应用内简体中文表述依据英文原量表，正式使用前仍需本地专业审校与年龄适用性确认。",
    )

    fun descriptor(type: AssessmentType): QuestionnaireDescriptor = when (type) {
        AssessmentType.PHQ_9 -> phq9
        AssessmentType.GAD_7 -> gad7
    }
}
