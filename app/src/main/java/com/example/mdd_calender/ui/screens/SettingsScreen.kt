package com.example.mdd_calender.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mdd_calender.data.IconPack
import com.example.mdd_calender.data.WeatherCondition
import com.example.mdd_calender.ui.MoodViewModel
import com.example.mdd_calender.ui.components.MoodType
import com.example.mdd_calender.ui.components.MoodVectorIcon
import com.example.mdd_calender.ui.components.glassmorphicCard
import com.example.mdd_calender.ui.theme.getWeatherColors

@Composable
fun SettingsScreen(
    viewModel: MoodViewModel,
    onBack: () -> Unit
) {
    val config by viewModel.iconConfig.collectAsState()
    val weatherColors = getWeatherColors(WeatherCondition.CLEAR, false)
    
    var selectedMoodForUpload by remember { mutableStateOf<MoodType?>(null) }
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null && selectedMoodForUpload != null) {
                viewModel.preferences.setCustomUri(selectedMoodForUpload!!.label, uri.toString())
            }
            selectedMoodForUpload = null
        }
    )

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
            
            // App Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .glassmorphicCard(cornerRadius = 24.dp, surfaceAlpha = 0.5f, shadowElevation = 4.dp)
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = weatherColors.textPrimary)
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Text(
                    "个性化设置", 
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = weatherColors.textPrimary
                    )
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Native Packs
            Text(
                "原生图标包", 
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = weatherColors.textPrimary)
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                IconPackCard(
                    title = "经典拟物",
                    isSelected = config.pack == IconPack.CLASSIC,
                    onClick = { viewModel.preferences.setIconPack(IconPack.CLASSIC) },
                    modifier = Modifier.weight(1f),
                    weatherColors = weatherColors
                )
                IconPackCard(
                    title = "极简几何",
                    isSelected = config.pack == IconPack.MINIMALIST,
                    onClick = { viewModel.preferences.setIconPack(IconPack.MINIMALIST) },
                    modifier = Modifier.weight(1f),
                    weatherColors = weatherColors
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Custom Upload
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "自定义情绪图片", 
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = weatherColors.textPrimary)
                )
                
                if (config.customUris.isNotEmpty()) {
                    Text(
                        "重置全部",
                        color = weatherColors.textPrimary.copy(alpha = 0.6f),
                        fontSize = 14.sp,
                        modifier = Modifier.clickable { viewModel.preferences.clearAllCustomUris() }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "点击下方图标，从相册上传图片替换原生表情。", 
                style = MaterialTheme.typography.bodyMedium.copy(color = weatherColors.textPrimary.copy(alpha = 0.6f))
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassmorphicCard(cornerRadius = 24.dp, surfaceAlpha = weatherColors.surfaceAlpha)
                    .padding(24.dp)
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    items(MoodType.entries) { mood ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable {
                                selectedMoodForUpload = mood
                                photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }
                        ) {
                            MoodVectorIcon(
                                mood = mood,
                                config = config,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = mood.label,
                                style = MaterialTheme.typography.bodyMedium.copy(color = weatherColors.textPrimary)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun IconPackCard(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    weatherColors: com.example.mdd_calender.ui.theme.WeatherColors
) {
    Box(
        modifier = modifier
            .height(100.dp)
            .glassmorphicCard(
                cornerRadius = 24.dp, 
                surfaceAlpha = if (isSelected) 0.3f else 0.1f,
                shadowElevation = if (isSelected) 8.dp else 0.dp
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                title, 
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = weatherColors.textPrimary
                )
            )
            if (isSelected) {
                Spacer(modifier = Modifier.height(8.dp))
                Icon(Icons.Default.CheckCircle, contentDescription = "Selected", tint = weatherColors.textPrimary, modifier = Modifier.size(20.dp))
            }
        }
    }
}
