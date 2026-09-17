package com.example.mdd_calender.data

import kotlinx.coroutines.flow.Flow

class MoodRepository(private val moodDao: MoodDao, private val anniversaryDao: AnniversaryDao) {

    fun getMoodRecordsForMonth(yearMonth: String): Flow<List<MoodRecord>> {
        return moodDao.getMoodRecordsForMonth(yearMonth)
    }

    fun getMoodRecordsByDate(date: String): Flow<List<MoodRecord>> {
        return moodDao.getMoodRecordsByDate(date)
    }

    suspend fun saveMoodRecord(record: MoodRecord) {
        moodDao.insertMoodRecord(record)
    }

    // Anniversary operations
    fun getAllAnniversaries(): Flow<List<AnniversaryRecord>> {
        return anniversaryDao.getAllAnniversaries()
    }

    suspend fun saveAnniversary(record: AnniversaryRecord) {
        anniversaryDao.insertAnniversary(record)
    }

    suspend fun deleteAnniversary(record: AnniversaryRecord) {
        anniversaryDao.deleteAnniversary(record)
    }
}
