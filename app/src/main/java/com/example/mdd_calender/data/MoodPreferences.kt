package com.example.mdd_calender.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class IconPack {
    CLASSIC,
    MINIMALIST
}

data class MoodIconConfig(
    val pack: IconPack = IconPack.CLASSIC,
    val customUris: Map<String, String> = emptyMap() // Map of MoodType label to Uri string
)

class MoodPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("mood_prefs", Context.MODE_PRIVATE)
    
    private val _configFlow = MutableStateFlow(loadConfig())
    val configFlow: StateFlow<MoodIconConfig> = _configFlow.asStateFlow()

    private fun loadConfig(): MoodIconConfig {
        val packName = prefs.getString("icon_pack", IconPack.CLASSIC.name) ?: IconPack.CLASSIC.name
        val pack = runCatching { IconPack.valueOf(packName) }.getOrDefault(IconPack.CLASSIC)
        
        val customUris = mutableMapOf<String, String>()
        // The 5 mood labels
        val labels = listOf("惊喜", "开心", "平淡", "难过", "糟糕")
        for (label in labels) {
            val uri = prefs.getString("custom_uri_$label", null)
            if (uri != null) {
                customUris[label] = uri
            }
        }
        return MoodIconConfig(pack, customUris)
    }

    fun setIconPack(pack: IconPack) {
        prefs.edit().putString("icon_pack", pack.name).apply()
        _configFlow.value = loadConfig()
    }

    fun setCustomUri(moodLabel: String, uri: String?) {
        val key = "custom_uri_$moodLabel"
        if (uri == null) {
            prefs.edit().remove(key).apply()
        } else {
            prefs.edit().putString(key, uri).apply()
        }
        _configFlow.value = loadConfig()
    }

    fun clearAllCustomUris() {
        val editor = prefs.edit()
        val labels = listOf("惊喜", "开心", "平淡", "难过", "糟糕")
        for (label in labels) {
            editor.remove("custom_uri_$label")
        }
        editor.apply()
        _configFlow.value = loadConfig()
    }
}
