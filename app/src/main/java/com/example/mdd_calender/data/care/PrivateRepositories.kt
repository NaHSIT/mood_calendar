package com.example.mdd_calender.data.care

import com.example.mdd_calender.domain.model.ActorContext
import com.example.mdd_calender.domain.model.ActorRole
import com.example.mdd_calender.domain.model.AssessmentForEvaluation
import com.example.mdd_calender.domain.model.AssessmentRecord
import com.example.mdd_calender.domain.model.AssessmentState
import com.example.mdd_calender.domain.model.AssessmentType
import com.example.mdd_calender.domain.model.CareFailure
import com.example.mdd_calender.domain.model.CareResult
import com.example.mdd_calender.domain.model.ConsentState
import com.example.mdd_calender.domain.model.HealthConsent
import com.example.mdd_calender.domain.model.HealthPullRequest
import com.example.mdd_calender.domain.model.HealthSampleType
import com.example.mdd_calender.domain.model.HealthScope
import com.example.mdd_calender.domain.model.RawHealthSample
import com.example.mdd_calender.domain.model.SampleQuality
import com.example.mdd_calender.domain.model.SymptomBand
import com.example.mdd_calender.domain.model.SystemUtcClock
import com.example.mdd_calender.domain.model.UtcClock
import com.example.mdd_calender.domain.port.AssessmentEvaluationSource
import com.example.mdd_calender.domain.port.AssessmentRepository
import com.example.mdd_calender.domain.port.ConsentRepository
import com.example.mdd_calender.domain.port.HealthSignalSource
import com.example.mdd_calender.domain.port.SessionProvider
import com.example.mdd_calender.domain.port.StudentHealthRepository
import com.example.mdd_calender.security.AndroidKeystoreCipher
import com.example.mdd_calender.security.AuthenticatedCipher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.nio.charset.StandardCharsets

private suspend fun SessionProvider.requireRole(role: ActorRole): CareResult<ActorContext> = when (val current = currentActor()) {
    is CareResult.Failure -> current
    is CareResult.Success -> if (current.value.role == role) current
    else CareResult.Failure(CareFailure.Forbidden("${role.name} session required"))
}

private fun String.bytes() = toByteArray(StandardCharsets.UTF_8)
private fun ByteArray.text() = toString(StandardCharsets.UTF_8)
private fun Set<*>.packed() = map { it.toString() }.sorted().joinToString(",")
private fun String.unpack() = if (isBlank()) emptyList() else split(',')

class RoomAssessmentRepository(
    private val dao: CareDao,
    private val session: SessionProvider,
    private val cipher: AuthenticatedCipher,
) : AssessmentRepository, AssessmentEvaluationSource {
    override suspend fun saveForCurrentStudent(record: AssessmentRecord): CareResult<AssessmentRecord> {
        val actor = when (val result = session.requireRole(ActorRole.STUDENT)) {
            is CareResult.Failure -> return result
            is CareResult.Success -> result.value
        }
        if (record.studentId != actor.actorId) return CareResult.Failure(CareFailure.Forbidden("Cannot save another student's assessment"))
        validate(record)?.let { return CareResult.Failure(it) }
        val encrypted = when (val result = cipher.encrypt(record.answers.joinToString(",").bytes(), AndroidKeystoreCipher.aad("assessment", record.assessmentId))) {
            is CareResult.Failure -> return result
            is CareResult.Success -> result.value
        }
        dao.upsertAssessment(record.toEntity(actor.dataDomain.name, encrypted))
        return CareResult.Success(record)
    }

    override suspend fun listForCurrentStudent(): CareResult<List<AssessmentRecord>> {
        val actor = when (val result = session.requireRole(ActorRole.STUDENT)) {
            is CareResult.Failure -> return result
            is CareResult.Success -> result.value
        }
        val output = mutableListOf<AssessmentRecord>()
        for (entity in dao.assessmentsForOwner(actor.actorId, actor.dataDomain.name)) {
            when (val decoded = entity.toModel(cipher)) {
                is CareResult.Failure -> return decoded
                is CareResult.Success -> output += decoded.value
            }
        }
        return CareResult.Success(output)
    }

    override suspend fun getForCurrentStudent(assessmentId: String): CareResult<AssessmentRecord> {
        val actor = when (val result = session.requireRole(ActorRole.STUDENT)) {
            is CareResult.Failure -> return result
            is CareResult.Success -> result.value
        }
        val entity = dao.assessmentForOwner(assessmentId, actor.actorId, actor.dataDomain.name) ?: run {
            if (dao.assessment(assessmentId, actor.dataDomain.name) != null) return CareResult.Failure(CareFailure.Forbidden("Assessment belongs to another student"))
            return CareResult.Failure(CareFailure.NotFound("assessment", assessmentId))
        }
        return entity.toModel(cipher)
    }

    override suspend fun completedForEvaluation(studentId: String, sinceEpochMillis: Long): CareResult<List<AssessmentForEvaluation>> {
        val actor = when (val result = session.requireRole(ActorRole.SYSTEM)) {
            is CareResult.Failure -> return result
            is CareResult.Success -> result.value
        }
        val output = mutableListOf<AssessmentForEvaluation>()
        for (entity in dao.completedAssessments(studentId, actor.dataDomain.name, sinceEpochMillis)) {
            val record = when (val decoded = entity.toModel(cipher)) {
                is CareResult.Failure -> return decoded
                is CareResult.Success -> decoded.value
            }
            val completedAt = record.completedAtEpochMillis ?: continue
            output += AssessmentForEvaluation(
                record.assessmentId, record.studentId, record.type, record.totalScore, record.symptomBand,
                record.type == AssessmentType.PHQ_9 && record.answers.getOrNull(8)?.let { it > 0 } == true,
                completedAt, record.scoringVersion,
            )
        }
        return CareResult.Success(output)
    }

    private fun validate(record: AssessmentRecord): CareFailure.InvalidInput? {
        val expected = if (record.type == AssessmentType.PHQ_9) 9 else 7
        if (record.answers.size != expected || record.answers.any { it !in 0..3 }) return CareFailure.InvalidInput("Invalid answer count or range")
        if (record.totalScore != record.answers.sum()) return CareFailure.InvalidInput("Total score does not match answers")
        if (record.state == AssessmentState.COMPLETED && record.completedAtEpochMillis == null) return CareFailure.InvalidInput("Completed assessment requires completion time")
        if (record.state == AssessmentState.DRAFT && record.completedAtEpochMillis != null) return CareFailure.InvalidInput("Draft cannot have completion time")
        return null
    }
}

private fun AssessmentRecord.toEntity(domain: String, encrypted: String) = AssessmentEntity(
    assessmentId, studentId, domain, type.name, instrumentVersion, encrypted, totalScore,
    symptomBand.name, state.name, completedAtEpochMillis, scoringVersion,
)

private fun AssessmentEntity.toModel(cipher: AuthenticatedCipher): CareResult<AssessmentRecord> =
    when (val plain = cipher.decrypt(encryptedAnswers, AndroidKeystoreCipher.aad("assessment", assessmentId))) {
        is CareResult.Failure -> plain
        is CareResult.Success -> runCatching {
            AssessmentRecord(
                assessmentId, studentId, AssessmentType.valueOf(type), instrumentVersion,
                plain.value.text().unpack().map(String::toInt), totalScore, SymptomBand.valueOf(symptomBand),
                AssessmentState.valueOf(state), completedAtEpochMillis, scoringVersion,
            )
        }.fold(
            onSuccess = { CareResult.Success(it) },
            onFailure = { CareResult.Failure(CareFailure.CryptographyFailure("Decrypted assessment payload is invalid")) },
        )
    }

class RoomConsentRepository(
    private val dao: CareDao,
    private val session: SessionProvider,
    private val clock: UtcClock = SystemUtcClock,
) : ConsentRepository {
    override suspend fun getForCurrentStudent(): CareResult<HealthConsent> {
        val actor = when (val result = session.requireRole(ActorRole.STUDENT)) {
            is CareResult.Failure -> return result
            is CareResult.Success -> result.value
        }
        val value = dao.consent(actor.actorId, actor.dataDomain.name)?.toModel() ?: HealthConsent(
            actor.actorId, emptySet(), ConsentState.REVOKED, 0, clock.nowEpochMillis(),
        )
        return CareResult.Success(value)
    }

    override suspend fun updateForCurrentStudent(scopes: Set<HealthScope>, granted: Boolean): CareResult<HealthConsent> {
        val actor = when (val result = session.requireRole(ActorRole.STUDENT)) {
            is CareResult.Failure -> return result
            is CareResult.Success -> result.value
        }
        if (granted && scopes.isEmpty()) return CareResult.Failure(CareFailure.InvalidInput("At least one health scope is required when granting consent"))
        val old = dao.consent(actor.actorId, actor.dataDomain.name)
        val updated = HealthConsent(actor.actorId, if (granted) scopes else emptySet(), if (granted) ConsentState.GRANTED else ConsentState.REVOKED, (old?.revision ?: 0) + 1, clock.nowEpochMillis())
        dao.upsertConsent(updated.toEntity(actor.dataDomain.name))
        return CareResult.Success(updated)
    }

    override fun observeForCurrentStudent(): Flow<CareResult<HealthConsent>> = flow {
        when (val actorResult = session.requireRole(ActorRole.STUDENT)) {
            is CareResult.Failure -> emit(actorResult)
            is CareResult.Success -> emitAll(dao.observeConsent(actorResult.value.actorId, actorResult.value.dataDomain.name).map { entity ->
                CareResult.Success(entity?.toModel() ?: HealthConsent(actorResult.value.actorId, emptySet(), ConsentState.REVOKED, 0, clock.nowEpochMillis()))
            })
        }
    }
}

private fun HealthConsent.toEntity(domain: String) = ConsentEntity(studentId, domain, scopes.packed(), state.name, revision, changedAtEpochMillis)
private fun ConsentEntity.toModel() = HealthConsent(studentId, scopes.unpack().map(HealthScope::valueOf).toSet(), ConsentState.valueOf(state), revision, changedAtEpochMillis)

class RoomStudentHealthRepository(
    private val dao: CareDao,
    private val session: SessionProvider,
    private val cipher: AuthenticatedCipher,
) : StudentHealthRepository, HealthSignalSource {
    override suspend fun saveForCurrentStudent(samples: List<RawHealthSample>): CareResult<Unit> {
        val actor = when (val result = session.requireRole(ActorRole.STUDENT)) {
            is CareResult.Failure -> return result
            is CareResult.Success -> result.value
        }
        if (samples.any { it.ownerId != actor.actorId }) return CareResult.Failure(CareFailure.Forbidden("Cannot save another student's health data"))
        val consent = dao.consent(actor.actorId, actor.dataDomain.name)
            ?: return CareResult.Failure(CareFailure.InvalidInput("Health consent is missing"))
        if (consent.state != ConsentState.GRANTED.name || samples.any { it.consentRevision != consent.revision }) {
            return CareResult.Failure(CareFailure.Conflict("Consent was revoked or revised"))
        }
        val allowedTypes = consent.scopes.unpack().flatMap {
            when (HealthScope.valueOf(it)) {
                HealthScope.HEART_RATE -> listOf(HealthSampleType.HEART_RATE_BPM)
                HealthScope.SLEEP -> listOf(HealthSampleType.SLEEP_DURATION_MINUTES, HealthSampleType.SLEEP_QUALITY)
            }
        }.toSet()
        if (samples.any { it.type !in allowedTypes }) return CareResult.Failure(CareFailure.Forbidden("Sample type is outside the granted consent scopes"))
        val entities = mutableListOf<HealthSampleEntity>()
        for (sample in samples) {
            val encrypted = when (val result = cipher.encrypt("${sample.value}\u0000${sample.unit}".bytes(), AndroidKeystoreCipher.aad("health", sample.sampleId))) {
                is CareResult.Failure -> return result
                is CareResult.Success -> result.value
            }
            entities += sample.toEntity(actor.dataDomain.name, encrypted)
        }
        dao.upsertSamples(entities)
        return CareResult.Success(Unit)
    }

    override suspend fun listForCurrentStudent(fromEpochMillis: Long, toEpochMillis: Long): CareResult<List<RawHealthSample>> {
        val actor = when (val result = session.requireRole(ActorRole.STUDENT)) {
            is CareResult.Failure -> return result
            is CareResult.Success -> result.value
        }
        return decodeAll(dao.samples(actor.actorId, actor.dataDomain.name, fromEpochMillis, toEpochMillis))
    }

    override suspend fun getForCurrentStudent(sampleId: String): CareResult<RawHealthSample> {
        val actor = when (val result = session.requireRole(ActorRole.STUDENT)) {
            is CareResult.Failure -> return result
            is CareResult.Success -> result.value
        }
        val entity = dao.sampleForOwner(sampleId, actor.actorId, actor.dataDomain.name) ?: run {
            if (dao.sample(sampleId, actor.dataDomain.name) != null) return CareResult.Failure(CareFailure.Forbidden("Health sample belongs to another student"))
            return CareResult.Failure(CareFailure.NotFound("health sample", sampleId))
        }
        return entity.toModel(cipher)
    }

    override suspend fun deleteForCurrentStudent(scopes: Set<HealthScope>): CareResult<Int> {
        val actor = when (val result = session.requireRole(ActorRole.STUDENT)) {
            is CareResult.Failure -> return result
            is CareResult.Success -> result.value
        }
        if (scopes.isEmpty()) return CareResult.Failure(CareFailure.InvalidInput("At least one health scope is required for deletion"))
        val types = scopes.flatMap {
            when (it) {
                HealthScope.HEART_RATE -> listOf(HealthSampleType.HEART_RATE_BPM)
                HealthScope.SLEEP -> listOf(HealthSampleType.SLEEP_DURATION_MINUTES, HealthSampleType.SLEEP_QUALITY)
            }
        }.map { it.name }
        return CareResult.Success(dao.deleteSamplesForOwner(actor.actorId, actor.dataDomain.name, types))
    }

    override suspend fun samplesForExtraction(request: HealthPullRequest): CareResult<List<RawHealthSample>> {
        val actor = when (val result = session.requireRole(ActorRole.SYSTEM)) {
            is CareResult.Failure -> return result
            is CareResult.Success -> result.value
        }
        val consent = dao.consent(request.studentId, actor.dataDomain.name)
            ?: return CareResult.Failure(CareFailure.InvalidInput("Health consent is missing"))
        if (consent.state != ConsentState.GRANTED.name || consent.revision != request.consentRevision) {
            return CareResult.Failure(CareFailure.Conflict("Consent was revoked or revised"))
        }
        return decodeAll(dao.samples(request.studentId, actor.dataDomain.name, request.fromEpochMillis, request.toEpochMillis))
    }

    private fun decodeAll(entities: List<HealthSampleEntity>): CareResult<List<RawHealthSample>> {
        val output = mutableListOf<RawHealthSample>()
        for (entity in entities) when (val decoded = entity.toModel(cipher)) {
            is CareResult.Failure -> return decoded
            is CareResult.Success -> output += decoded.value
        }
        return CareResult.Success(output)
    }
}

private fun RawHealthSample.toEntity(domain: String, encrypted: String) = HealthSampleEntity(
    sampleId, ownerId, domain, source, type.name, measuredAtEpochMillis, encrypted, quality.name, simulated, consentRevision,
)

private fun HealthSampleEntity.toModel(cipher: AuthenticatedCipher): CareResult<RawHealthSample> =
    when (val plain = cipher.decrypt(encryptedValueAndUnit, AndroidKeystoreCipher.aad("health", sampleId))) {
        is CareResult.Failure -> plain
        is CareResult.Success -> runCatching {
            val parts = plain.value.text().split('\u0000', limit = 2)
            RawHealthSample(sampleId, ownerId, source, HealthSampleType.valueOf(type), measuredAtEpochMillis, parts[0].toDouble(), parts[1], SampleQuality.valueOf(quality), simulated, consentRevision)
        }.fold(
            onSuccess = { CareResult.Success(it) },
            onFailure = { CareResult.Failure(CareFailure.CryptographyFailure("Decrypted health payload is invalid")) },
        )
    }
