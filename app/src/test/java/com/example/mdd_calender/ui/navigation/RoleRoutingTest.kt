package com.example.mdd_calender.ui.navigation

import com.example.mdd_calender.domain.model.ActorRole
import org.junit.Assert.assertEquals
import org.junit.Test

class RoleRoutingTest {
    @Test
    fun `student and teacher identities land in separate shells`() {
        assertEquals(RoleLanding.STUDENT_SPACE, landingForRole(ActorRole.STUDENT))
        assertEquals(RoleLanding.TEACHER_WORKBENCH, landingForRole(ActorRole.TEACHER))
    }

    @Test
    fun `internal system identity cannot open an app shell`() {
        assertEquals(RoleLanding.DENIED, landingForRole(ActorRole.SYSTEM))
    }
}
