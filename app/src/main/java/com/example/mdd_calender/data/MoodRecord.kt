package com.example.mdd_calender.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mood_records")
data class MoodRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: String, // Format: YYYY-MM-DD
    val time: String, // Format: HH:mm
    val moodType: String,
    val note: String?,
    val content: String? = null,
    val imageUris: String? = null, // Store as comma-separated string
    val createdAt: Long,
    val updatedAt: Long
)
