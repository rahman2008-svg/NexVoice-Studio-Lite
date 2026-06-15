package com.example.data

import kotlinx.coroutines.flow.Flow

class AudioRepository(private val dao: AudioRecordDao) {
    val allRecords: Flow<List<AudioRecord>> = dao.getAllRecords()

    suspend fun insert(record: AudioRecord): Long {
        return dao.insertRecord(record)
    }

    suspend fun deleteById(id: Int) {
        dao.deleteRecordById(id)
    }
}
