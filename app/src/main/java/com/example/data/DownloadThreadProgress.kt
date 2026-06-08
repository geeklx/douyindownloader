package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "download_thread_progress")
data class DownloadThreadProgress(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val downloadId: Long,
    val threadId: Int,
    val startBytes: Long,
    val endBytes: Long,
    val downloadedBytes: Long
)
