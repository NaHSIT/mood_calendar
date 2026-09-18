package com.example.mdd_calender.feature.teacher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mdd_calender.domain.model.CareFailure
import com.example.mdd_calender.domain.model.CareResult
import com.example.mdd_calender.domain.model.DeliveryRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class TeacherInboxFilter { NEW, IN_PROGRESS, CLOSED }

data class TeacherWorkbenchUiState(
    val loading: Boolean = false,
    val filter: TeacherInboxFilter = TeacherInboxFilter.NEW,
    val items: List<TeacherInboxItem> = emptyList(),
    val exitReviews: List<TeacherExitReviewItem> = emptyList(),
    val selected: TeacherAlertDetails? = null,
    val lastDelivery: DeliveryRecord? = null,
    val message: String? = null,
)

class TeacherWorkbenchViewModel(
    private val service: TeacherWorkbenchService,
) : ViewModel() {
    private val _state = MutableStateFlow(TeacherWorkbenchUiState())
    val state: StateFlow<TeacherWorkbenchUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        _state.value = _state.value.copy(loading = true, message = null)
        viewModelScope.launch {
            val inbox = service.loadInbox()
            val reviews = service.loadPendingExitReviews()
            when {
                inbox is CareResult.Failure -> showFailure(inbox.error)
                reviews is CareResult.Failure -> showFailure(reviews.error)
                inbox is CareResult.Success && reviews is CareResult.Success -> _state.value = _state.value.copy(
                    loading = false,
                    items = inbox.value,
                    exitReviews = reviews.value,
                )
            }
        }
    }

    fun select(eventId: String) {
        viewModelScope.launch {
            when (val result = service.loadDetails(eventId)) {
                is CareResult.Failure -> showFailure(result.error)
                is CareResult.Success -> _state.value = _state.value.copy(selected = result.value, message = null)
            }
        }
    }

    fun clearSelection() {
        _state.value = _state.value.copy(selected = null, lastDelivery = null)
        refresh()
    }

    fun setFilter(filter: TeacherInboxFilter) { _state.value = _state.value.copy(filter = filter) }

    fun acknowledge(caseId: String) {
        viewModelScope.launch {
            when (val result = service.acknowledge(caseId)) {
                is CareResult.Failure -> showFailure(result.error)
                is CareResult.Success -> { refresh(); select(result.value.alertId) }
            }
        }
    }

    fun start(caseId: String, policyVersion: String = "demo-teacher-follow-up-v1") {
        viewModelScope.launch {
            when (val result = service.startIntervention(caseId, policyVersion)) {
                is CareResult.Failure -> showFailure(result.error)
                is CareResult.Success -> { _state.value = _state.value.copy(message = "干预已启动，AA 已入库"); refresh(); select(result.value.intervention.alertId) }
            }
        }
    }

    fun close(caseId: String, note: String) {
        viewModelScope.launch {
            when (val result = service.closeIntervention(caseId, note)) {
                is CareResult.Failure -> showFailure(result.error)
                is CareResult.Success -> { refresh(); select(result.value.alertId) }
            }
        }
    }

    fun simulateDelivery(eventId: String) {
        viewModelScope.launch {
            when (val result = service.simulateDelivery(eventId)) {
                is CareResult.Failure -> showFailure(result.error)
                is CareResult.Success -> _state.value = _state.value.copy(lastDelivery = result.value, message = deliveryMessage(result.value))
            }
        }
    }

    fun reviewExit(enrollmentId: String, approve: Boolean, note: String) {
        viewModelScope.launch {
            when (val result = service.reviewExit(enrollmentId, approve, note)) {
                is CareResult.Failure -> showFailure(result.error)
                is CareResult.Success -> {
                    _state.value = _state.value.copy(message = if (approve) "退出审核已批准" else "退出审核已驳回")
                    refresh()
                }
            }
        }
    }

    private fun deliveryMessage(delivery: DeliveryRecord): String = when (delivery.status.name) {
        "SIMULATED_DELIVERED" -> "模拟平台已回执（不代表教师已确认）"
        "RETRY_PENDING" -> "模拟平台暂时失败，已进入重试"
        else -> "模拟平台投递失败"
    }

    private fun showFailure(error: CareFailure) {
        _state.value = _state.value.copy(loading = false, message = failureMessage(error))
    }

    private fun failureMessage(error: CareFailure): String = when (error) {
        is CareFailure.Forbidden -> "无权访问该预警"
        is CareFailure.NotFound -> "记录不存在或已不可用"
        is CareFailure.Conflict -> error.reason
        is CareFailure.NotConfigured -> "平台未接入"
        is CareFailure.InvalidInput -> error.reason
        else -> "操作失败，请稍后重试"
    }
}
