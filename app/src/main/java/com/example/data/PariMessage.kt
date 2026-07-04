package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pari_messages")
data class PariMessage(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val role: String, // "user" or "pari"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val mode: String, // "chat", "tts", "podcast", "audiobook"
    val audioPath: String? = null,
    val scriptTitle: String? = null,
    val attachedFileName: String? = null,
    val isAudioGenerated: Boolean = false
)
