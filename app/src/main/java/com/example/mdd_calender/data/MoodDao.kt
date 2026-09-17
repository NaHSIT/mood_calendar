package com.example.mdd_calender.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MoodDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMoodRecord(record: MoodRecord)

    @Query("SELECT * FROM mood_records WHERE date = :date ORDER BY time ASC")
    fun getMoodRecordsByDate(date: String): Flow<List<MoodRecord>>

    @Query("SELECT * FROM mood_records WHERE date LIKE :yearMonth || '%'")
    fun getMoodRecordsForMonth(yearMonth: String): Flow<List<MoodRecord>>
}
