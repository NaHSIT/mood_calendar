package com.example.mdd_calender.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mdd_calender.feature.teacher.TeacherWorkbenchRoute
import com.example.mdd_calender.feature.teacher.TeacherWorkbenchViewModel

@Composable
fun DemoRoleEntryScreen(
    onStudentDemo: () -> Unit,
    onTeacherDemo: () -> Unit,
    preparing: Boolean = false,
    preparationMessage: String? = null,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth().padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Icon(Icons.Outlined.Lock, null, tint = MaterialTheme.colorScheme.primary)
            Text("心情日历", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("请选择独立的演示身份", style = MaterialTheme.typography.titleMedium)
            Text(
                "正式系统应由学校账号认证结果决定角色。这里仅用于比赛演示，进入后不能在学生页面直接取得教师权限。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            RoleEntryCard(
                title = "学生演示入口",
                description = "进入个人情绪日历、数据洞察、随访任务和隐私设置",
                icon = { Icon(Icons.Outlined.Person, null) },
                onClick = onStudentDemo,
                enabled = !preparing,
            )
            RoleEntryCard(
                title = "教师演示入口",
                description = "使用已授权的演示教师身份进入脱敏预警工作台",
                icon = { Icon(Icons.Outlined.School, null) },
                onClick = onTeacherDemo,
                enabled = !preparing,
            )
            if (preparing) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator()
                    Text("正在准备虚构演示数据…")
                }
            }
            preparationMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Text("演示身份与真实数据域隔离，不代表已接入学校统一认证。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RoleEntryCard(
    title: String,
    description: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.padding(2.dp)) {
                Box(Modifier.padding(14.dp), contentAlignment = Alignment.Center) { icon() }
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun TeacherMainScreen(viewModel: TeacherWorkbenchViewModel, onLogout: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    androidx.compose.material3.Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Outlined.Badge, "预警工作台") },
                    label = { Text("预警工作台") },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Outlined.Settings, "账号设置") },
                    label = { Text("账号设置") },
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (tab == 0) TeacherWorkbenchRoute(viewModel) else TeacherAccountScreen(onLogout)
        }
    }
}

@Composable
private fun TeacherAccountScreen(onLogout: () -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Text("教师账号", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("当前为已授权的演示教师身份", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("demo-teacher", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("权限范围：仅查看被分配学生的脱敏预警摘要和处理信息")
                    HorizontalDivider()
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Outlined.HealthAndSafety, null, tint = MaterialTheme.colorScheme.primary)
                        Text("不可访问私人日记、逐题答案或原始健康数据", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item { OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) { Text("退出教师演示") } }
    }
}
