package com.example.mdd_calender.domain.port

import com.example.mdd_calender.domain.model.ActorContext
import com.example.mdd_calender.domain.model.CareResult
import kotlinx.coroutines.flow.Flow

interface SessionProvider {
    suspend fun currentActor(): CareResult<ActorContext>
    fun observeActor(): Flow<CareResult<ActorContext>>
}

/** Production implementations must verify an external credential and never trust a UI-selected role. */
fun interface IdentityAuthenticator {
    suspend fun authenticate(credential: String): CareResult<ActorContext>
}
