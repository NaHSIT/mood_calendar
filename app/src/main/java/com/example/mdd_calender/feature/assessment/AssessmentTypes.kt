package com.example.mdd_calender.feature.assessment

import com.example.mdd_calender.domain.model.AssessmentType as DomainAssessmentType
import com.example.mdd_calender.domain.model.SymptomBand as DomainSymptomBand

/** B-module aliases to the frozen A contracts; no duplicate public domain enums. */
typealias AssessmentType = DomainAssessmentType
typealias SymptomBand = DomainSymptomBand

val AssessmentType.itemCount: Int
    get() = if (this == AssessmentType.PHQ_9) 9 else 7

val AssessmentType.maximumScore: Int
    get() = itemCount * 3
