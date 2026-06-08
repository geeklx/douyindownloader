package com.example.data

import kotlinx.coroutines.flow.Flow

class DownloadRepository(private val downloadDao: DownloadDao) {
    val allItems: Flow<List<DownloadItem>> = downloadDao.getAllItems()

    suspend fun getItemById(id: Long): DownloadItem? {
        return downloadDao.getItemById(id)
    }

    suspend fun insertItem(item: DownloadItem): Long {
        return downloadDao.insertItem(item)
    }

    suspend fun updateItem(item: DownloadItem) {
        downloadDao.updateItem(item)
    }

    suspend fun deleteItem(item: DownloadItem) {
        downloadDao.deleteItem(item)
        downloadDao.deleteThreadsForDownload(item.id)
    }

    suspend fun deleteItemById(id: Long) {
        downloadDao.deleteItemById(id)
        downloadDao.deleteThreadsForDownload(id)
    }

    suspend fun getThreadsForDownload(downloadId: Long): List<DownloadThreadProgress> {
        return downloadDao.getThreadsForDownload(downloadId)
    }

    suspend fun insertThreadProgress(progress: DownloadThreadProgress) {
        downloadDao.insertThreadProgress(progress)
    }

    suspend fun updateThreadProgress(downloadId: Long, threadId: Int, downloadedBytes: Long) {
        downloadDao.updateThreadProgress(downloadId, threadId, downloadedBytes)
    }

    suspend fun deleteThreadsForDownload(downloadId: Long) {
        downloadDao.deleteThreadsForDownload(downloadId)
    }
}
