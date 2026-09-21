package com.example.mdd_calender.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mdd_calender.ui.MoodViewModel
import com.example.mdd_calender.data.AnniversaryRecord
import com.example.mdd_calender.ui.components.glassmorphicCard
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnniversaryScreen(
    viewModel: MoodViewModel,
    onBack: () -> Unit
) {
    val anniversaries by viewModel.anniversaries.collectAsState()
    
    var showAddDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<AnniversaryRecord?>(null) }
    var newTitle by remember { mutableStateOf("") }
    var newDate by remember { mutableStateOf(LocalDate.now().plusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE)) }
    var isCountdown by remember { mutableStateOf(true) }
    var dateError by remember { mutableStateOf<String?>(null) }

    fun openEditor(record: AnniversaryRecord? = null) {
        editing = record
        newTitle = record?.title.orEmpty()
        newDate = record?.targetDate ?: LocalDate.now().plusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE)
        isCountdown = record?.isCountdown ?: true
        dateError = null
        showAddDialog = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F9FC))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(56.dp))
            
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .glassmorphicCard(cornerRadius = 24.dp, surfaceAlpha = 0.5f, shadowElevation = 4.dp)
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.Black)
                }
                
                Text(
                    text = "纪念日与倒数",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                )
                
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .glassmorphicCard(cornerRadius = 24.dp, surfaceAlpha = 0.5f, shadowElevation = 4.dp)
                        .clickable { openEditor() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.Black)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            if (anniversaries.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("还没有记录任何重要日子", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    item { Text("点击任意卡片即可修改标题、日期和倒计时设置。", color = Color.Gray, style = MaterialTheme.typography.bodySmall) }
                    items(anniversaries) { ann ->
                        val targetDate = LocalDate.parse(ann.targetDate)
                        val daysDiff = ChronoUnit.DAYS.between(LocalDate.now(), targetDate)
                        val isPast = daysDiff < 0
                        val displayDays = Math.abs(daysDiff)
                        val color = Color(android.graphics.Color.parseColor(ann.colorHex))
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .glassmorphicCard(cornerRadius = 24.dp, surfaceAlpha = 1f, shadowElevation = 8.dp)
                                .background(color.copy(alpha = 0.1f))
                                .clickable { openEditor(ann) }
                                .padding(20.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = ann.title,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            color = Color.Black,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "目标日期: ${ann.targetDate}",
                                        style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                                    )
                                }
                                
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        text = if (ann.isCountdown && !isPast) "还剩" else "已过",
                                        style = MaterialTheme.typography.labelMedium.copy(color = Color.Gray)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "$displayDays",
                                        style = MaterialTheme.typography.displaySmall.copy(
                                            color = color,
                                            fontWeight = FontWeight.Black
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "天",
                                        style = MaterialTheme.typography.labelMedium.copy(color = Color.Gray)
                                    )
                                }
                                
                                Spacer(modifier = Modifier.width(16.dp))
                                IconButton(onClick = { viewModel.deleteAnniversary(ann) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "删除 ${ann.title}", tint = Color.Gray.copy(alpha = 0.7f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(if (editing == null) "添加重要日子" else "编辑重要日子") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text("标题 (如: 考研, 恋爱纪念)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = newDate,
                        onValueChange = { newDate = it; dateError = null },
                        label = { Text("日期 (YYYY-MM-DD)") },
                        isError = dateError != null,
                        supportingText = { dateError?.let { Text(it) } },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = isCountdown,
                            onCheckedChange = { isCountdown = it }
                        )
                        Text("作为倒计时")
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val validDate = runCatching { LocalDate.parse(newDate) }.isSuccess
                    if (!validDate) {
                        dateError = "请输入有效日期，例如 2026-10-01"
                    } else if (newTitle.isNotBlank()) {
                        viewModel.saveAnniversary(
                            id = editing?.id ?: 0,
                            title = newTitle,
                            targetDate = newDate,
                            isCountdown = isCountdown,
                            colorHex = editing?.colorHex ?: "#FF4081",
                            createdAt = editing?.createdAt ?: System.currentTimeMillis(),
                        )
                        showAddDialog = false
                    }
                }) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false; editing = null }) {
                    Text("取消")
                }
            }
        )
    }
}
