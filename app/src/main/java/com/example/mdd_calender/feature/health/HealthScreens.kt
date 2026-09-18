@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.mdd_calender.feature.health

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale as ComposeLocale
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale as JavaLocale

data class HealthManagementUiState(
    val consent: HealthConsentSnapshot,
    val capabilities: List<HealthProviderCapability>,
    val isBusy: Boolean = false,
    val statusMessage: String? = null,
)

@Composable
fun HealthManagementScreen(
    state: HealthManagementUiState,
    onBack: () -> Unit,
    onConsentChange: (HealthMetric, Boolean) -> Unit,
    onDeleteRawData: (Set<HealthMetric>) -> Unit,
    onOpenRawData: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("健康数据管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            NoticeCard(
                title = if (state.consent.domain == HealthDataDomain.DEMO) "演示数据模式" else "健康数据说明",
                body = "心率和睡眠默认关闭，可分别授权。原始数据仅学生本人可见；教师只可能看到不含原始序列的粗粒度辅助标签。辅助信号不是诊断。",
            )
            HealthMetric.entries.forEach { metric ->
                val capability = state.capabilities.firstOrNull { it.metric == metric }
                ConsentCard(
                    metric = metric,
                    enabled = state.consent.allows(metric),
                    consentRevision = state.consent.revision,
                    capability = capability,
                    isBusy = state.isBusy,
                    onCheckedChange = { onConsentChange(metric, it) },
                )
            }
            state.statusMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            Button(onClick = onOpenRawData, modifier = Modifier.fillMaxWidth()) {
                Text("查看我的原始数据")
            }
            OutlinedButton(
                onClick = { onDeleteRawData(HealthMetric.entries.toSet()) },
                enabled = !state.isBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.DeleteOutline, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("删除已保存的原始数据")
            }
            Text(
                "关闭同步与删除数据是两件事。撤回会停止后续同步，但不会自动删除已保存的原始数据；删除也不会收回教师已经看到的最小化历史摘要。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ConsentCard(
    metric: HealthMetric,
    enabled: Boolean,
    consentRevision: Long,
    capability: HealthProviderCapability?,
    isBusy: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val available = capability?.availability == HealthProviderAvailability.AVAILABLE
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (metric == HealthMetric.HEART_RATE) Icons.Default.FavoriteBorder else Icons.Default.NightsStay,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (metric == HealthMetric.HEART_RATE) "心率" else "睡眠", fontWeight = FontWeight.SemiBold)
                    Text(
                        capability?.description ?: "此数据类型不可用",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onCheckedChange,
                    enabled = available && !isBusy,
                )
            }
            HorizontalDivider()
            Text(
                if (metric == HealthMetric.HEART_RATE) {
                    "用途：生成时效和质量明确的粗粒度心率辅助信号。"
                } else {
                    "用途：生成时效和质量明确的粗粒度睡眠辅助信号。"
                },
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "当前状态：${if (enabled) "已授权（修订 $consentRevision）" else "未授权"}",
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun NoticeCard(title: String, body: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
fun RawHealthDataScreen(
    view: RawHealthDataView,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("我的原始健康数据") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (view) {
                RawHealthDataView.AccessDenied -> EmptyState(
                    "没有访问权限",
                    "原始健康数据仅限数据所属学生本人查看。",
                    MaterialTheme.colorScheme.error,
                )
                RawHealthDataView.NotConnected -> EmptyState(
                    "尚未接入健康数据源",
                    "应用没有读取设备或手机健康平台，也没有可展示的实时数据。",
                )
                is RawHealthDataView.Data -> {
                    if (view.isDemo) NoticeCard("模拟数据", "以下曲线和列表均为虚构演示数据，不来自真实设备。")
                    if (view.samples.isEmpty()) {
                        EmptyState("暂无数据", "当前没有已保存的原始数据；可返回健康数据管理查看授权状态。")
                    } else {
                        HealthSampleBars(view.samples)
                        view.samples.sortedByDescending { it.observedAtEpochMillis }.forEach { sample ->
                            SampleRow(sample)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HealthSampleBars(samples: List<HealthSample>) {
    val shown = samples.sortedBy { it.observedAtEpochMillis }.takeLast(12)
    val max = shown.maxOfOrNull { it.value } ?: 1.0
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("最近数据概览", fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth().height(100.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                shown.forEach { sample ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height((12 + 88 * sample.value / max).dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)),
                    )
                }
            }
        }
    }
}

@Composable
private fun SampleRow(sample: HealthSample) {
    val observableLocale = ComposeLocale.current
    val javaLocale = JavaLocale.forLanguageTag(observableLocale.toLanguageTag())
    val date = SimpleDateFormat("MM-dd HH:mm", javaLocale).format(Date(sample.observedAtEpochMillis))
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (sample.metric == HealthMetric.HEART_RATE) "心率" else "睡眠", fontWeight = FontWeight.Medium)
                Text(date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${sample.value} ${sample.unit}")
                Text("质量 ${(sample.quality * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun EmptyState(title: String, message: String, titleColor: Color = MaterialTheme.colorScheme.onSurface) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = titleColor)
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
