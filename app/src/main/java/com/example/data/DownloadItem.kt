package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "download_items")
data class DownloadItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val originalUrl: String,
    val title: String,
    val coverUrl: String,
    val localPath: String,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val status: String = "PENDING", // PENDING, DOWNLOADING, PAUSED, COMPLETED, ERROR
    val createdAt: Long = System.currentTimeMillis()
)
