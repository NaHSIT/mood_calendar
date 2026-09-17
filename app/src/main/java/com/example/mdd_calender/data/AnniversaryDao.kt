package com.example.mdd_calender.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AnniversaryDao {
    @Query("SELECT * FROM anniversary_records ORDER BY targetDate ASC")
    fun getAllAnniversaries(): Flow<List<AnniversaryRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnniversary(record: AnniversaryRecord)

    @Delete
    suspend fun deleteAnniversary(record: AnniversaryRecord)
}
