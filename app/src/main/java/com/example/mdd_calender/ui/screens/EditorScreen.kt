package com.example.mdd_calender.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.mdd_calender.data.WeatherCondition
import com.example.mdd_calender.ui.MoodViewModel
import com.example.mdd_calender.ui.components.MoodType
import com.example.mdd_calender.ui.components.MoodVectorIcon
import com.example.mdd_calender.ui.components.glassmorphicCard
import com.example.mdd_calender.ui.theme.getWeatherColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: MoodViewModel,
    date: String,
    id: Int,
    onBack: () -> Unit
) {
    var selectedMood by remember { mutableStateOf(MoodType.GOOD) }
    var content by remember { mutableStateOf("") }
    var time by remember { mutableStateOf(java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))) }
    var imageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    val context = LocalContext.current
    
    val selectedRecords by viewModel.selectedDateRecords.collectAsState()

    LaunchedEffect(date) {
        viewModel.selectDate(java.time.LocalDate.parse(date))
    }

    LaunchedEffect(selectedRecords, id) {
        val record = selectedRecords.find { it.id == id }
        if (record != null) {
            MoodType.fromLabel(record.moodType)?.let { selectedMood = it }
            content = record.content ?: record.note ?: ""
            time = record.time
            if (!record.imageUris.isNullOrEmpty()) {
                imageUris = record.imageUris.split(",").map { Uri.parse(it) }
            } else {
                imageUris = emptyList()
            }
        } else {
            selectedMood = MoodType.GOOD
            content = ""
            time = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
            imageUris = emptyList()
        }
    }
    
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
        onResult = { uris ->
            uris.forEach { uri ->
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            }
            imageUris = (imageUris + uris).distinct()
        }
    )

    val config by viewModel.iconConfig.collectAsState()

    val weatherColors = getWeatherColors(WeatherCondition.CLEAR, false)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(weatherColors.backgroundStart, weatherColors.backgroundEnd)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(56.dp))
            
            // Custom App Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .glassmorphicCard(cornerRadius = 24.dp, surfaceAlpha = 0.5f, shadowElevation = 4.dp)
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = weatherColors.textPrimary)
                }
                
                Text(
                    date, 
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = weatherColors.textPrimary
                    )
                )
                
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .glassmorphicCard(cornerRadius = 24.dp, surfaceAlpha = 0.8f, shadowElevation = 4.dp)
                        .clickable { 
                            val uriString = if (imageUris.isEmpty()) null else imageUris.joinToString(",") { it.toString() }
                            viewModel.saveMoodWithContent(id, date, time, selectedMood.label, content, uriString)
                            onBack()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Check, contentDescription = "保存心情记录", tint = weatherColors.textPrimary)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            // Time field
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "记录时间: ",
                    style = MaterialTheme.typography.bodyMedium.copy(color = weatherColors.textPrimary.copy(alpha = 0.6f))
                )
                BasicTextField(
                    value = time,
                    onValueChange = { time = it },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = weatherColors.textPrimary,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.width(60.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            // Mood Selector
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassmorphicCard(cornerRadius = 32.dp, surfaceAlpha = weatherColors.surfaceAlpha)
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    MoodType.entries.forEach { mood ->
                        val isSelected = selectedMood == mood
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) weatherColors.textPrimary.copy(alpha = 0.1f) else Color.Transparent)
                                .clickable { selectedMood = mood },
                            contentAlignment = Alignment.Center
                        ) {
                            MoodVectorIcon(
                                mood = mood, 
                                modifier = Modifier.size(if (isSelected) 40.dp else 32.dp),
                                config = config
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Text Editor (Borderless)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .glassmorphicCard(cornerRadius = 32.dp, surfaceAlpha = weatherColors.surfaceAlpha)
                    .padding(24.dp)
            ) {
                if (content.isEmpty()) {
                    Text(
                        "今天发生了什么？", 
                        style = TextStyle(
                            fontSize = 18.sp,
                            color = weatherColors.textPrimary.copy(alpha = 0.4f),
                            lineHeight = 28.sp
                        )
                    )
                }
                BasicTextField(
                    value = content,
                    onValueChange = { content = it },
                    modifier = Modifier.fillMaxSize(),
                    textStyle = TextStyle(
                        fontSize = 18.sp,
                        color = weatherColors.textPrimary,
                        lineHeight = 28.sp
                    ),
                    decorationBox = { innerTextField -> innerTextField() }
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Image Picker
            if (imageUris.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(imageUris) { uri ->
                        AsyncImage(
                            model = uri,
                            contentDescription = "心情记录图片",
                            modifier = Modifier
                                .size(120.dp)
                                .clip(RoundedCornerShape(24.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .glassmorphicCard(cornerRadius = 28.dp, surfaceAlpha = 0.2f, shadowElevation = 0.dp)
                    .clickable { 
                        photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) 
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = "添加记录图片", tint = weatherColors.textPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "添加照片", 
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = weatherColors.textPrimary
                        )
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
