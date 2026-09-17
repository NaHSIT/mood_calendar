package com.example.mdd_calender.security

import com.example.mdd_calender.domain.model.ActorContext
import com.example.mdd_calender.domain.model.CareFailure
import com.example.mdd_calender.domain.model.CareResult
import com.example.mdd_calender.domain.port.IdentityAuthenticator
import com.example.mdd_calender.domain.port.SessionProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** A fixed, injected demo identity. It deliberately has no public role-switching API. */
class DemoSessionProvider(actor: ActorContext) : SessionProvider {
    private val state = MutableStateFlow<CareResult<ActorContext>>(CareResult.Success(actor))
    override suspend fun currentActor(): CareResult<ActorContext> = state.value
    override fun observeActor(): Flow<CareResult<ActorContext>> = state
}

class UnauthenticatedSessionProvider : SessionProvider {
    private val result = CareResult.Failure(CareFailure.Unauthenticated)
    override suspend fun currentActor(): CareResult<ActorContext> = result
    override fun observeActor(): Flow<CareResult<ActorContext>> = MutableStateFlow(result)
}

/** Safe production default until a real identity adapter is configured. */
object RejectingIdentityAuthenticator : IdentityAuthenticator {
    override suspend fun authenticate(credential: String): CareResult<ActorContext> =
        CareResult.Failure(CareFailure.NotConfigured("production identity authentication"))
}
