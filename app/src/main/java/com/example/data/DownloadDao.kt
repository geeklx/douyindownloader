package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM download_items ORDER BY createdAt DESC")
    fun getAllItems(): Flow<List<DownloadItem>>

    @Query("SELECT * FROM download_items WHERE id = :id")
    suspend fun getItemById(id: Long): DownloadItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: DownloadItem): Long

    @Update
    suspend fun updateItem(item: DownloadItem)

    @Delete
    suspend fun deleteItem(item: DownloadItem)

    @Query("DELETE FROM download_items WHERE id = :id")
    suspend fun deleteItemById(id: Long)

    @Query("SELECT * FROM download_thread_progress WHERE downloadId = :downloadId")
    suspend fun getThreadsForDownload(downloadId: Long): List<DownloadThreadProgress>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertThreadProgress(progress: DownloadThreadProgress)

    @Query("UPDATE download_thread_progress SET downloadedBytes = :downloadedBytes WHERE downloadId = :downloadId AND threadId = :threadId")
    suspend fun updateThreadProgress(downloadId: Long, threadId: Int, downloadedBytes: Long)

    @Query("DELETE FROM download_thread_progress WHERE downloadId = :downloadId")
    suspend fun deleteThreadsForDownload(downloadId: Long)
}
