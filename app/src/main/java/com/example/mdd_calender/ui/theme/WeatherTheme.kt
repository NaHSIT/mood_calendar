package com.example.mdd_calender.ui.theme

import androidx.compose.ui.graphics.Color
import com.example.mdd_calender.data.WeatherCondition

data class WeatherColors(
    val backgroundStart: Color,
    val backgroundEnd: Color,
    val surfaceAlpha: Float,
    val textPrimary: Color
)

fun getWeatherColors(condition: WeatherCondition, isDark: Boolean): WeatherColors {
    return when (condition) {
        WeatherCondition.CLEAR -> WeatherColors(
            backgroundStart = Color(0xFFFFE0B2),
            backgroundEnd = Color(0xFFFFF8E1),
            surfaceAlpha = 0.45f,
            textPrimary = Color(0xFF4E342E)
        )
        WeatherCondition.RAIN -> WeatherColors(
            backgroundStart = Color(0xFFB0BEC5),
            backgroundEnd = Color(0xFFE0E0E0),
            surfaceAlpha = 0.5f,
            textPrimary = Color(0xFF263238)
        )
        WeatherCondition.CLOUDY -> WeatherColors(
            backgroundStart = Color(0xFFCFD8DC),
            backgroundEnd = Color(0xFFECEFF1),
            surfaceAlpha = 0.55f,
            textPrimary = Color(0xFF37474F)
        )
        WeatherCondition.SNOW -> WeatherColors(
            backgroundStart = Color(0xFFE3F2FD),
            backgroundEnd = Color(0xFFF3E5F5),
            surfaceAlpha = 0.6f,
            textPrimary = Color(0xFF1565C0)
        )
        WeatherCondition.THUNDERSTORM -> WeatherColors(
            backgroundStart = Color(0xFF78909C),
            backgroundEnd = Color(0xFFB0BEC5),
            surfaceAlpha = 0.4f,
            textPrimary = Color(0xFF212121)
        )
        WeatherCondition.FOG -> WeatherColors(
            backgroundStart = Color(0xFFD7CCC8),
            backgroundEnd = Color(0xFFEFEBE9),
            surfaceAlpha = 0.5f,
            textPrimary = Color(0xFF3E2723)
        )
    }
}
