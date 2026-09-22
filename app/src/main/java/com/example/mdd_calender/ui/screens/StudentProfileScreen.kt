package com.example.mdd_calender.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mdd_calender.ui.components.CareEntry

@Composable
fun StudentProfileScreen(
    onHealth: () -> Unit,
    onSettings: () -> Unit,
    onLogout: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text("我的", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("管理个人数据、隐私授权和账号设置", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { CareEntry("健康数据与授权", "查看授权状态、本人原始数据与删除入口", Icons.Outlined.HealthAndSafety, onHealth) }
        item { CareEntry("隐私与个性化设置", "管理界面偏好和个人记录选项", Icons.Outlined.Settings, onSettings) }
        item {
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Lock, null, tint = MaterialTheme.colorScheme.primary)
                Text("数据归属说明", fontWeight = FontWeight.Bold)
                Text("私人日记、量表逐题答案和原始健康数据仅本人可访问。教师端只接收脱敏预警摘要。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.DeleteOutline, null)
                Text("退出学生演示", Modifier.padding(start = 8.dp))
            }
        }
    }
}
