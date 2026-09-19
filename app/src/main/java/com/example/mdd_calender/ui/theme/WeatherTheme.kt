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
    if (isDark) {
        return when (condition) {
            WeatherCondition.CLEAR -> WeatherColors(Color(0xFF171B26), Color(0xFF24202A), 0.72f, Color(0xFFF5F7FB))
            WeatherCondition.RAIN -> WeatherColors(Color(0xFF131B24), Color(0xFF202B36), 0.72f, Color(0xFFF2F6FA))
            WeatherCondition.CLOUDY -> WeatherColors(Color(0xFF1A1E25), Color(0xFF292D35), 0.72f, Color(0xFFF4F5F7))
            WeatherCondition.SNOW -> WeatherColors(Color(0xFF18212B), Color(0xFF242334), 0.74f, Color(0xFFF3F7FF))
            WeatherCondition.THUNDERSTORM -> WeatherColors(Color(0xFF11151C), Color(0xFF242936), 0.76f, Color(0xFFF5F6FA))
            WeatherCondition.FOG -> WeatherColors(Color(0xFF211E20), Color(0xFF302B2D), 0.72f, Color(0xFFF7F3F4))
        }
    }
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
