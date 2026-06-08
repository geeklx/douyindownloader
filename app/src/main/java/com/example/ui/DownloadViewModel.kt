package com.example.ui

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.DownloadDatabase
import com.example.data.DownloadItem
import com.example.data.DownloadRepository
import com.example.utils.DownloadEngine
import com.example.utils.DownloadProgressEvent
import com.example.utils.DouyinParser
import com.example.utils.ParsedVideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import com.example.ui.theme.AppThemePreset

sealed interface ParseState {
    object Idle : ParseState
    object Parsing : ParseState
    data class Parsed(val info: ParsedVideoInfo) : ParseState
    data class Error(val message: String) : ParseState
}

class DownloadViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "DownloadViewModel"

    private val repository: DownloadRepository
    val historyItems: StateFlow<List<DownloadItem>>

    // Input States
    private val _urlInput = MutableStateFlow("")
    val urlInput = _urlInput.asStateFlow()

    // Parse status flow
    private val _parseState = MutableStateFlow<ParseState>(ParseState.Idle)
    val parseState = _parseState.asStateFlow()

    // Clipboard auto-prompt dialog control
    private val _clipboardPromptUrl = MutableStateFlow<String?>(null)
    val clipboardPromptUrl = _clipboardPromptUrl.asStateFlow()

    // Video preview target
    private val _previewUrl = MutableStateFlow<String?>(null)
    val previewUrl = _previewUrl.asStateFlow()

    private val _previewTitle = MutableStateFlow<String?>(null)
    val previewTitle = _previewTitle.asStateFlow()

    // Theme selection preset and local cache persistence
    private val _currentThemePreset = MutableStateFlow(AppThemePreset.DOUYIN_DARK)
    val currentThemePreset = _currentThemePreset.asStateFlow()
    private val prefs = application.getSharedPreferences("app_settings_prefs", Context.MODE_PRIVATE)

    // Bulk selection modes
    private val _selectedItemIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedItemIds = _selectedItemIds.asStateFlow()

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode = _isSelectionMode.asStateFlow()

    // Keep track of clipboard link already checked in this run to avoid annoying repetitive dialogs
    private var lastCheckedClipboardUrl: String? = null

    init {
        // Load saved theme preset
        val savedThemeIdx = prefs.getInt("selected_theme_preset_idx", 0)
        _currentThemePreset.value = AppThemePreset.values().getOrElse(savedThemeIdx) { AppThemePreset.DOUYIN_DARK }

        val database = DownloadDatabase.getDatabase(application)
        repository = DownloadRepository(database.downloadDao())
        historyItems = repository.allItems.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        // Observe progress events from DownloadEngine
        viewModelScope.launch {
            DownloadEngine.events.collect { event ->
                when (event) {
                    is DownloadProgressEvent.Progress -> {
                        // DB updates handled in DownloadEngine periodically, ViewModel is notified of general updates.
                    }
                    is DownloadProgressEvent.Completed -> {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(getApplication(), "视频下载完成！", Toast.LENGTH_SHORT).show()
                        }
                    }
                    is DownloadProgressEvent.Paused -> {
                        // Paused stream handled
                    }
                    is DownloadProgressEvent.Error -> {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(getApplication(), "下载错误: ${event.errorMessage}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    fun setUrlInput(url: String) {
        _urlInput.value = url
    }

    fun clearUrlInput() {
        _urlInput.value = ""
    }

    fun setParseState(state: ParseState) {
        _parseState.value = state
    }

    // Auto clipboard detection
    fun checkClipboardOnStart(clipboardText: String) {
        val extractedUrl = DouyinParser.extractUrl(clipboardText)
        if (extractedUrl != null && extractedUrl != lastCheckedClipboardUrl) {
            // Check if this URL is already in our completed download items or currently downloading
            viewModelScope.launch {
                val exists = historyItems.value.any { it.originalUrl == extractedUrl && it.status == "COMPLETED" }
                if (!exists) {
                    _clipboardPromptUrl.value = extractedUrl
                    lastCheckedClipboardUrl = extractedUrl
                }
            }
        }
    }

    fun dismissClipboardPrompt() {
        _clipboardPromptUrl.value = null
    }

    // Direct url parse trigger
    fun triggerParse(url: String) {
        if (url.trim().isEmpty()) {
            _parseState.value = ParseState.Error("请输入正确的抖音分享/复制链接")
            return
        }

        _parseState.value = ParseState.Parsing
        viewModelScope.launch {
            try {
                Log.d(TAG, "Trigger parsing for: $url")
                val parsed = DouyinParser.parseUrl(url)
                if (parsed != null) {
                    _parseState.value = ParseState.Parsed(parsed)
                } else {
                    _parseState.value = ParseState.Error("解析失败，未提取到有效无水印视频。您也可以先手动添加并尝试下载。")
                }
            } catch (e: Exception) {
                _parseState.value = ParseState.Error("解析异常: ${e.message}")
            }
        }
    }

    // Begin fresh download
    fun startNewDownload(info: ParsedVideoInfo) {
        viewModelScope.launch {
            try {
                // Determine a safe filepath
                val moviesDir = getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                    ?: getApplication<Application>().filesDir
                val sanitizedTitle = info.title.replace("[\\\\/:*?\"<>|]".toRegex(), "_")
                    .take(30) // Limit to 30 characters for clean filename
                val filename = "Douyin_${sanitizedTitle}_${System.currentTimeMillis()}.mp4"
                val destFile = File(moviesDir, filename)

                val item = DownloadItem(
                    url = info.videoUrl,
                    originalUrl = info.originalUrl,
                    title = info.title,
                    coverUrl = info.coverUrl,
                    localPath = destFile.absolutePath,
                    status = "PENDING"
                )

                val id = repository.insertItem(item)
                val insertedItem = repository.getItemById(id)
                if (insertedItem != null) {
                    DownloadEngine.startDownload(getApplication(), insertedItem)
                    Toast.makeText(getApplication(), "已加入下载队列", Toast.LENGTH_SHORT).show()
                }

                // Clean parsing state
                _parseState.value = ParseState.Idle
                _urlInput.value = ""
            } catch (e: Exception) {
                Toast.makeText(getApplication(), "启动下载失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // History controls
    fun togglePlayPause(item: DownloadItem) {
        if (item.status == "DOWNLOADING") {
            DownloadEngine.pauseDownload(getApplication(), item.id)
        } else {
            DownloadEngine.startDownload(getApplication(), item)
        }
    }

    fun deleteItem(item: DownloadItem) {
        viewModelScope.launch {
            // Cancel downloading first if running
            if (item.status == "DOWNLOADING") {
                DownloadEngine.pauseDownload(getApplication(), item.id)
            }
            // Delete local file
            val file = File(item.localPath)
            if (file.exists()) {
                file.delete()
            }
            repository.deleteItem(item)
            Toast.makeText(getApplication(), "已删除记录", Toast.LENGTH_SHORT).show()
        }
    }

    // Video preview controls
    fun setPreviewVideo(url: String?, title: String?) {
        _previewUrl.value = url
        _previewTitle.value = title
    }

    fun clearPreviewVideo() {
        _previewUrl.value = null
        _previewTitle.value = null
    }

    // Multi-selection
    fun toggleSelectionMode() {
        if (_isSelectionMode.value) {
            _selectedItemIds.value = emptySet()
        }
        _isSelectionMode.value = !_isSelectionMode.value
    }

    fun toggleItemSelection(id: Long) {
        val current = _selectedItemIds.value.toMutableSet()
        if (current.contains(id)) {
            current.remove(id)
        } else {
            current.add(id)
        }
        _selectedItemIds.value = current
    }

    fun selectAll() {
        val allIds = historyItems.value.map { it.id }.toSet()
        _selectedItemIds.value = allIds
    }

    fun clearSelection() {
        _selectedItemIds.value = emptySet()
    }

    fun bulkDelete() {
        val idsToDelete = _selectedItemIds.value
        if (idsToDelete.isEmpty()) return

        viewModelScope.launch {
            var deletedCount = 0
            historyItems.value.filter { idsToDelete.contains(it.id) }.forEach { item ->
                if (item.status == "DOWNLOADING") {
                    DownloadEngine.pauseDownload(getApplication(), item.id)
                }
                val file = File(item.localPath)
                if (file.exists()) {
                    file.delete()
                }
                repository.deleteItem(item)
                deletedCount++
            }
            _isSelectionMode.value = false
            _selectedItemIds.value = emptySet()
            Toast.makeText(getApplication(), "已批量删除 $deletedCount 条记录", Toast.LENGTH_SHORT).show()
        }
    }

    // Export local video to standard platform Photo/Video Gallery
    fun exportToGallery(context: Context, item: DownloadItem) {
        val file = File(item.localPath)
        if (!file.exists() || item.status != "COMPLETED") {
            Toast.makeText(context, "视频文件未下载完成，无法导出", Toast.LENGTH_SHORT).show()
            return
        }

        viewModelScope.launch {
            try {
                val exported = withContext(Dispatchers.IO) {
                    val displayName = "Douyin_${item.title.replace("[\\\\/:*?\"<>|]".toRegex(), "_")}_${System.currentTimeMillis()}.mp4"
                    
                    val contentValues = ContentValues().apply {
                        put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/去水印视频")
                            put(MediaStore.Video.Media.IS_PENDING, 1)
                        }
                    }

                    val resolver = context.contentResolver
                    val collection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    } else {
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    }

                    val videoUri = resolver.insert(collection, contentValues)
                    if (videoUri != null) {
                        resolver.openOutputStream(videoUri)?.use { outStream ->
                            file.inputStream().use { inStream ->
                                inStream.copyTo(outStream)
                            }
                        }

                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                            contentValues.clear()
                            contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                            resolver.update(videoUri, contentValues, null, null)
                        }
                        true
                    } else {
                        false
                    }
                }

                if (exported) {
                    Toast.makeText(context, "已成功导出至相册！", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "导出失败", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Export failure", e)
                Toast.makeText(context, "导出异常: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Share action (Share original file or download link)
    fun shareVideo(context: Context, item: DownloadItem) {
        viewModelScope.launch {
            val file = File(item.localPath)
            if (file.exists() && item.status == "COMPLETED") {
                try {
                    val authority = "${context.packageName}.fileprovider"
                    val fileUri = FileProvider.getUriForFile(context, authority, file)

                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "video/mp4"
                        putExtra(Intent.EXTRA_STREAM, fileUri)
                        putExtra(Intent.EXTRA_SUBJECT, item.title)
                        putExtra(Intent.EXTRA_TEXT, "${item.title} (无水印视频分享)")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "分享视频文件"))
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to share video file, falling back to link sharing", e)
                    // Fallback to sharing the direct URL
                    shareText(context, item.title, item.url)
                }
            } else {
                // If local file not complete, share item url / info
                shareText(context, item.title, item.url)
            }
        }
    }

    fun selectThemePreset(preset: AppThemePreset) {
        _currentThemePreset.value = preset
        prefs.edit().putInt("selected_theme_preset_idx", preset.ordinal).apply()
    }

    private fun shareText(context: Context, title: String, content: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "$title \n视频播放链接: $content")
        }
        context.startActivity(Intent.createChooser(shareIntent, "分享链接"))
    }
}
