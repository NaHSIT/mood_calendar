package com.example.mdd_calender.feature.assessment

data class QuestionnaireDescriptor(
    val type: AssessmentType,
    val displayName: String,
    val version: String,
    val recallPeriod: String,
    val itemPlaceholders: List<String>,
    val sourceUrl: String,
    val contentNotice: String,
)

/**
 * The item labels below are intentionally placeholders, not a claimed official Chinese edition.
 * Production wording, age suitability, licensing, and professional review remain external inputs.
 */
object QuestionnaireCatalog {
    val phq9 = QuestionnaireDescriptor(
        type = AssessmentType.PHQ_9,
        displayName = "PHQ-9 情绪筛查（原型）",
        version = "prototype-zh-pending-review",
        recallPeriod = "请回顾过去两周",
        itemPlaceholders = (1..9).map { "PHQ-9 第 $it 题（中文题文待专业审核）" },
        sourceUrl = "https://www.hiv.uw.edu/page/mental-health-screening/phq-9",
        contentNotice = "本原型仅展示填写与计分流程，不提供诊断；中文题文、年龄适用性及授权状态待审核。",
    )

    val gad7 = QuestionnaireDescriptor(
        type = AssessmentType.GAD_7,
        displayName = "GAD-7 焦虑筛查（原型）",
        version = "prototype-zh-pending-review",
        recallPeriod = "请回顾过去两周",
        itemPlaceholders = (1..7).map { "GAD-7 第 $it 题（中文题文待专业审核）" },
        sourceUrl = "https://www.hiv.uw.edu/page/mental-health-screening/gad-7",
        contentNotice = "本原型仅展示填写与计分流程，不提供诊断；中文题文、年龄适用性及授权状态待审核。",
    )

    fun descriptor(type: AssessmentType): QuestionnaireDescriptor = when (type) {
        AssessmentType.PHQ_9 -> phq9
        AssessmentType.GAD_7 -> gad7
    }
}
