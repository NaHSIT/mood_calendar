package com.example.mdd_calender.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "anniversary_records")
data class AnniversaryRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val targetDate: String, // Format: YYYY-MM-DD
    val isCountdown: Boolean,
    val colorHex: String = "#FF9800",
    val createdAt: Long = System.currentTimeMillis()
)
