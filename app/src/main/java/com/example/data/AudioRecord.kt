package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "audio_records")
data class AudioRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val text: String,
    val filePath: String,
    val voiceStyle: String,
    val pitch: Float,
    val speed: Float,
    val createdAt: Long,
    val durationMs: Long
)
