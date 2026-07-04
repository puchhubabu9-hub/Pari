package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PariDao {
    @Query("SELECT * FROM pari_messages ORDER BY timestamp ASC")
    fun getAllMessagesFlow(): Flow<List<PariMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: PariMessage): Long

    @Update
    suspend fun updateMessage(message: PariMessage)

    @Query("DELETE FROM pari_messages WHERE id = :id")
    suspend fun deleteMessageById(id: Int)

    @Query("DELETE FROM pari_messages")
    suspend fun clearHistory()
}
