package com.example.utils

import android.content.Context
import android.util.Log
import com.example.data.DownloadDatabase
import com.example.data.DownloadItem
import com.example.data.DownloadThreadProgress
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

sealed class DownloadProgressEvent {
    data class Progress(val downloadId: Long, val downloadedBytes: Long, val totalBytes: Long) : DownloadProgressEvent()
    data class Completed(val downloadId: Long, val filePath: String) : DownloadProgressEvent()
    data class Paused(val downloadId: Long) : DownloadProgressEvent()
    data class Error(val downloadId: Long, val errorMessage: String) : DownloadProgressEvent()
}

object DownloadEngine {
    private const val TAG = "DownloadEngine"
    private const val NUM_THREADS = 3

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // Map to keep track of active download jobs
    private val activeJobs = ConcurrentHashMap<Long, Job>()

    // Shared Flow to emit download events to the ViewModel
    private val _events = MutableSharedFlow<DownloadProgressEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<DownloadProgressEvent> = _events

    fun startDownload(context: Context, item: DownloadItem) {
        val database = DownloadDatabase.getDatabase(context)
        val dao = database.downloadDao()

        // Cancel any existing job for this download first
        activeJobs[item.id]?.cancel()

        val job = CoroutineScope(Dispatchers.IO).launch {
            try {
                // Update status to DOWNLOADING in DB
                dao.updateItem(item.copy(status = "DOWNLOADING"))

                val localFile = File(item.localPath)
                if (!localFile.parentFile.exists()) {
                    localFile.parentFile.mkdirs()
                }

                Log.d(TAG, "Starting download for ${item.title} to path ${item.localPath}")

                // Step 1: Query content length and check support for Range
                val headRequest = Request.Builder()
                    .url(item.url)
                    .head()
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build()

                var contentLength = -1L
                var acceptRanges = false

                client.newCall(headRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        contentLength = response.header("Content-Length")?.toLongOrNull() ?: -1L
                        val ranges = response.header("Accept-Ranges")
                        acceptRanges = (ranges != null && ranges.contains("bytes")) || contentLength > 0
                        Log.d(TAG, "HEAD successful. Content-Length: $contentLength, Supports Range: $acceptRanges")
                    } else {
                        Log.w(TAG, "HEAD request unsuccessful ${response.code}, falling back to GET check")
                    }
                }

                // If HEAD failed or size unknown, double check using a short GET request or single stream
                if (contentLength <= 0) {
                    val getRequest = Request.Builder()
                        .url(item.url)
                        .header("Range", "bytes=0-1")
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .build()

                    client.newCall(getRequest).execute().use { response ->
                        if (response.isSuccessful) {
                            val rangeHeader = response.header("Content-Range")
                            val fullLength = rangeHeader?.substringAfterLast("/")?.toLongOrNull()
                            contentLength = fullLength ?: response.body?.contentLength() ?: -1L
                            acceptRanges = response.code == 206
                            Log.d(TAG, "GET size check successful. Content-Length: $contentLength, Support Range: $acceptRanges")
                        }
                    }
                }

                // Update total content size if available
                if (contentLength > 0 && item.totalBytes != contentLength) {
                    dao.updateItem(dao.getItemById(item.id)!!.copy(totalBytes = contentLength))
                }

                // Check if server supports multi-threaded range downloads
                if (acceptRanges && contentLength > 0) {
                    Log.d(TAG, "Running multi-threaded segmented downloader...")
                    runMultiThreadedDownload(context, item, contentLength, NUM_THREADS)
                } else {
                    Log.d(TAG, "Range not supported or file size unknown. Running single-threaded downloader...")
                    runSingleThreadedDownload(context, item, contentLength)
                }

            } catch (e: CancellationException) {
                Log.d(TAG, "Download job for ${item.id} cancelled/paused.")
                dao.updateItem(dao.getItemById(item.id)?.copy(status = "PAUSED") ?: return@launch)
                _events.emit(DownloadProgressEvent.Paused(item.id))
            } catch (e: Exception) {
                Log.e(TAG, "Download failed with exception for ${item.id}", e)
                dao.updateItem(dao.getItemById(item.id)?.copy(status = "ERROR") ?: return@launch)
                _events.emit(DownloadProgressEvent.Error(item.id, e.message ?: "未知网络错误"))
            } finally {
                activeJobs.remove(item.id)
            }
        }

        activeJobs[item.id] = job
    }

    fun pauseDownload(context: Context, downloadId: Long) {
        val job = activeJobs.remove(downloadId)
        if (job != null) {
            job.cancel()
        } else {
            // If job not active but DB has DOWNLOADING status, reset it to PAUSED
            CoroutineScope(Dispatchers.IO).launch {
                val dao = DownloadDatabase.getDatabase(context).downloadDao()
                val current = dao.getItemById(downloadId)
                if (current != null && current.status == "DOWNLOADING") {
                    dao.updateItem(current.copy(status = "PAUSED"))
                    _events.emit(DownloadProgressEvent.Paused(downloadId))
                }
            }
        }
    }

    // Single-threaded fallback mode
    private suspend fun runSingleThreadedDownload(context: Context, item: DownloadItem, totalLength: Long) = withContext(Dispatchers.IO) {
        val database = DownloadDatabase.getDatabase(context)
        val dao = database.downloadDao()

        val localFile = File(item.localPath)
        // Resume not supported natively on single-threaded fallback, so delete and start over
        if (localFile.exists()) {
            localFile.delete()
        }

        val request = Request.Builder()
            .url(item.url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("HTTP 错误码: ${response.code}")
            }

            val body = response.body ?: throw Exception("响应体为空")
            val inputStream: InputStream = body.byteStream()
            val outputStream = localFile.outputStream()

            val buffer = ByteArray(8192)
            var bytesRead: Int
            var totalDownloaded = 0L
            val finalTotalBytes = if (totalLength > 0) totalLength else body.contentLength()

            val updateInterval = 300L // update every 300ms to avoid DB lock
            var lastUpdate = System.currentTimeMillis()

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                ensureActive() // Check if paused/cancelled
                outputStream.write(buffer, 0, bytesRead)
                totalDownloaded += bytesRead

                val now = System.currentTimeMillis()
                if (now - lastUpdate > updateInterval) {
                    dao.updateItem(dao.getItemById(item.id)!!.copy(
                        downloadedBytes = totalDownloaded,
                        totalBytes = finalTotalBytes
                    ))
                    _events.emit(DownloadProgressEvent.Progress(item.id, totalDownloaded, finalTotalBytes))
                    lastUpdate = now
                }
            }

            outputStream.flush()
            outputStream.close()
            inputStream.close()

            // Verify size
            Log.d(TAG, "Single-thread download success! Local size: ${localFile.length()}")
            dao.updateItem(dao.getItemById(item.id)!!.copy(
                status = "COMPLETED",
                downloadedBytes = totalDownloaded,
                totalBytes = if (totalLength > 0) totalLength else totalDownloaded
            ))
            _events.emit(DownloadProgressEvent.Completed(item.id, item.localPath))
        }
    }

    // High performance multi-threaded range downloader with breakpoint resume
    private suspend fun runMultiThreadedDownload(
        context: Context,
        item: DownloadItem,
        totalLength: Long,
        numThreads: Int
    ) = withContext(Dispatchers.IO) {
        val database = DownloadDatabase.getDatabase(context)
        val dao = database.downloadDao()

        // 1. Fetch or create thread parts
        var parts = dao.getThreadsForDownload(item.id)
        if (parts.isEmpty()) {
            Log.d(TAG, "Creating new download segments for multi-threaded download profile.")
            val partSize = totalLength / numThreads
            val tempParts = mutableListOf<DownloadThreadProgress>()
            for (i in 0 until numThreads) {
                val start = i * partSize
                val end = if (i == numThreads - 1) totalLength - 1 else (i + 1) * partSize - 1
                val progress = DownloadThreadProgress(
                    downloadId = item.id,
                    threadId = i,
                    startBytes = start,
                    endBytes = end,
                    downloadedBytes = 0L
                )
                dao.insertThreadProgress(progress)
                tempParts.add(progress)
            }
            parts = tempParts
        } else {
            Log.d(TAG, "Resuming existing multi-threaded download profile. Found ${parts.size} segments.")
        }

        // Initialize target file size using RandomAccessFile
        val localFile = File(item.localPath)
        RandomAccessFile(localFile, "rwd").use { raf ->
            if (raf.length() < totalLength) {
                raf.setLength(totalLength)
            }
        }

        // Keep track of current progress of each thread
        val threadProgressMap = ConcurrentHashMap<Int, Long>()
        parts.forEach { threadProgressMap[it.threadId] = it.downloadedBytes }

        // Last progress emission tracker
        var lastEmitTime = System.currentTimeMillis()

        // Run segment workers
        val jobs = parts.map { part ->
            launch {
                val threadId = part.threadId
                var threadDownloaded = threadProgressMap[threadId] ?: 0L
                val segmentStart = part.startBytes + threadDownloaded
                val segmentEnd = part.endBytes

                // If this thread already completed its part, skip it!
                if (segmentStart > segmentEnd) {
                    Log.d(TAG, "Part $threadId already completed previously.")
                    return@launch
                }

                Log.d(TAG, "Thread $threadId fetching range $segmentStart-$segmentEnd")

                val request = Request.Builder()
                    .url(item.url)
                    .header("Range", "bytes=$segmentStart-$segmentEnd")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build()

                client.newCall(request).execute().use { response ->
                    // Expected HTTP 206 Partial Content
                    if (response.code != 206 && response.code != 200) {
                        throw Exception("Thread $threadId: HTTP 错误码 ${response.code}")
                    }

                    val body = response.body ?: throw Exception("Thread $threadId: 响应体为空")
                    val inputStream = body.byteStream()

                    RandomAccessFile(localFile, "rwd").use { fileRAF ->
                        fileRAF.seek(segmentStart)

                        val buffer = ByteArray(8192)
                        var bytesRead: Int

                        var threadLastSaveTime = System.currentTimeMillis()

                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            ensureActive() // Check if job is cancelled/paused
                            fileRAF.write(buffer, 0, bytesRead)
                            threadDownloaded += bytesRead
                            threadProgressMap[threadId] = threadDownloaded

                            // Calculate current total downloaded
                            val currentTotalDownloaded = threadProgressMap.values.sum()

                            // Frequently update the memory progress state, and update DB/notify UI in throttled periods
                            val now = System.currentTimeMillis()

                            // Thread local database save throttling (every 800ms per thread)
                            if (now - threadLastSaveTime > 800L) {
                                dao.updateThreadProgress(item.id, threadId, threadDownloaded)
                                threadLastSaveTime = now
                            }

                            // Global UI progress broadcast throttling (every 300ms)
                            if (now - lastEmitTime > 300L) {
                                dao.updateItem(dao.getItemById(item.id)!!.copy(
                                    downloadedBytes = currentTotalDownloaded
                                ))
                                _events.emit(DownloadProgressEvent.Progress(item.id, currentTotalDownloaded, totalLength))
                                lastEmitTime = now
                            }
                        }
                    }

                    // Save final thread progress
                    dao.updateThreadProgress(item.id, threadId, threadDownloaded)
                }
            }
        }

        // Wait for all threads to complete
        jobs.joinAll()

        // Sync & Final Verification of total size
        val finalDownloaded = threadProgressMap.values.sum()
        Log.d(TAG, "All segments finished! Total bytes written: $finalDownloaded")

        if (finalDownloaded >= totalLength) {
            // Delete thread indices since file completed successfully
            dao.deleteThreadsForDownload(item.id)
            dao.updateItem(dao.getItemById(item.id)!!.copy(
                status = "COMPLETED",
                downloadedBytes = totalLength
            ))
            _events.emit(DownloadProgressEvent.Completed(item.id, item.localPath))
            Log.d(TAG, "Download complete for ${item.title}")
        } else {
            // If size is shorter, it must have been stopped/paused
            Log.w(TAG, "Segments finished but size mismatched ($finalDownloaded / $totalLength)")
            dao.updateItem(dao.getItemById(item.id)!!.copy(
                status = "PAUSED",
                downloadedBytes = finalDownloaded
            ))
            _events.emit(DownloadProgressEvent.Paused(item.id))
        }
    }
}
