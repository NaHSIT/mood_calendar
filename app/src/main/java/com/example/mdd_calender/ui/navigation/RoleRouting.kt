package com.example.mdd_calender.ui.navigation

import com.example.mdd_calender.domain.model.ActorRole

enum class RoleLanding { STUDENT_SPACE, TEACHER_WORKBENCH, DENIED }

fun landingForRole(role: ActorRole): RoleLanding = when (role) {
    ActorRole.STUDENT -> RoleLanding.STUDENT_SPACE
    ActorRole.TEACHER -> RoleLanding.TEACHER_WORKBENCH
    ActorRole.SYSTEM -> RoleLanding.DENIED
}
