package com.example.mdd_calender.domain.port

import com.example.mdd_calender.domain.model.CareResult
import com.example.mdd_calender.domain.model.HealthCapability
import com.example.mdd_calender.domain.model.HealthConsent
import com.example.mdd_calender.domain.model.HealthPullRequest
import com.example.mdd_calender.domain.model.HealthScope
import com.example.mdd_calender.domain.model.PhysiologySignal
import com.example.mdd_calender.domain.model.RawHealthSample
import kotlinx.coroutines.flow.Flow

interface ConsentRepository {
    suspend fun getForCurrentStudent(): CareResult<HealthConsent>
    suspend fun updateForCurrentStudent(scopes: Set<HealthScope>, granted: Boolean): CareResult<HealthConsent>
    fun observeForCurrentStudent(): Flow<CareResult<HealthConsent>>
}

interface StudentHealthRepository {
    suspend fun saveForCurrentStudent(samples: List<RawHealthSample>): CareResult<Unit>
    suspend fun listForCurrentStudent(fromEpochMillis: Long, toEpochMillis: Long): CareResult<List<RawHealthSample>>
    suspend fun getForCurrentStudent(sampleId: String): CareResult<RawHealthSample>
}

/** Application-internal signal input; implementations require a SYSTEM session. */
interface HealthSignalSource {
    suspend fun samplesForExtraction(request: HealthPullRequest): CareResult<List<RawHealthSample>>
}

interface HealthDataProvider {
    suspend fun capability(): HealthCapability
    suspend fun requestAuthorization(scopes: Set<HealthScope>): CareResult<Set<HealthScope>>
    suspend fun revokeAuthorization(scopes: Set<HealthScope>): CareResult<Unit>
    suspend fun cancelSync(studentId: String, consentRevision: Long): CareResult<Unit>
    suspend fun pull(request: HealthPullRequest): CareResult<List<RawHealthSample>>
}

fun interface PhysiologySignalExtractor {
    suspend fun extract(samples: List<RawHealthSample>, consent: HealthConsent): CareResult<List<PhysiologySignal>>
}
