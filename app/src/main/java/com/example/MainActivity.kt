package com.example

import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.utils.ParsedVideoInfo
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.data.DownloadItem
import com.example.ui.DownloadViewModel
import com.example.ui.ParseState
import com.example.ui.theme.MyApplicationTheme
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainAppScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen() {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val viewModel: DownloadViewModel = viewModel()

    // Screen State variables
    var currentTab by remember { mutableStateOf(0) } // 0 = Downloader, 1 = Downloads Records
    var searchQuery by remember { mutableStateOf("") }

    // Obstream flows
    val urlInput by viewModel.urlInput.collectAsState()
    val parseState by viewModel.parseState.collectAsState()
    val clipboardPromptUrl by viewModel.clipboardPromptUrl.collectAsState()
    val historyItems by viewModel.historyItems.collectAsState()
    val previewUrl by viewModel.previewUrl.collectAsState()
    val previewTitle by viewModel.previewTitle.collectAsState()
    val selectedItemIds by viewModel.selectedItemIds.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()

    // Clipboard Listener
    val clipboardManager = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }
    val windowInfo = LocalWindowInfo.current

    LaunchedEffect(windowInfo.isWindowFocused) {
        if (windowInfo.isWindowFocused) {
            try {
                val clipData = clipboardManager.primaryClip
                if (clipData != null && clipData.itemCount > 0) {
                    val clipText = clipData.getItemAt(0).text?.toString() ?: ""
                    viewModel.checkClipboardOnStart(clipText)
                }
            } catch (e: Exception) {
                // Fail silently for background secure restrictions
            }
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DownloadForOffline,
                                contentDescription = "Logo",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Text(
                            text = if (isSelectionMode) "已选择 ${selectedItemIds.size} 项" else "TikSaver Pro",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    if (currentTab == 1) {
                        if (isSelectionMode) {
                            IconButton(onClick = { viewModel.selectAll() }) {
                                Icon(Icons.Filled.DoneAll, contentDescription = "全选")
                            }
                            IconButton(
                                onClick = { viewModel.bulkDelete() },
                                modifier = Modifier.testTag("bulk_delete_button")
                            ) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = "批量删除", tint = MaterialTheme.colorScheme.error)
                            }
                            IconButton(onClick = { viewModel.toggleSelectionMode() }) {
                                Icon(Icons.Default.Close, contentDescription = "取消选择")
                            }
                        } else {
                            IconButton(onClick = { viewModel.toggleSelectionMode() }) {
                                Icon(Icons.Filled.FormatListBulleted, contentDescription = "编辑/选择")
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                modifier = Modifier.navigationBarsPadding()
            ) {
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    icon = { Icon(Icons.Default.Download, contentDescription = "去水印下载") },
                    label = { Text("解析下载") }
                )
                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    icon = { Icon(Icons.Default.SlowMotionVideo, contentDescription = "下载记录") },
                    label = { Text("下载记录") }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (currentTab == 0) {
                    // TAB 0: DOWNLOAD PANEL
                    DownloaderPanel(
                        viewModel = viewModel,
                        urlInput = urlInput,
                        parseState = parseState,
                        focusManager = focusManager,
                        clipboardManager = clipboardManager
                    )
                } else {
                    // TAB 1: HISTORY PANEL
                    HistoryRecordsPanel(
                        viewModel = viewModel,
                        searchQuery = searchQuery,
                        onSearchChange = { searchQuery = it },
                        historyItems = historyItems,
                        isSelectionMode = isSelectionMode,
                        selectedItemIds = selectedItemIds
                    )
                }
            }

            // Custom floating system video player preview pane (Supports overlay / playing during downloads)
            if (previewUrl != null) {
                VideoPreviewOverlay(
                    url = previewUrl!!,
                    title = previewTitle ?: "无水印播放",
                    onCloseEvent = { viewModel.clearPreviewVideo() },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                )
            }

            // Clipboard prompt dialog
            if (clipboardPromptUrl != null) {
                AlertDialog(
                    onDismissRequest = { viewModel.dismissClipboardPrompt() },
                    icon = { Icon(Icons.Filled.Link, contentDescription = "检测到链接", tint = MaterialTheme.colorScheme.primary) },
                    title = { Text("发现抖音复制链接") },
                    text = {
                        Text(
                            text = "剪贴板检测到可能有视频链接：\n\n${clipboardPromptUrl}\n\n是否立即开始解析？",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.setUrlInput(clipboardPromptUrl!!)
                                viewModel.triggerParse(clipboardPromptUrl!!)
                                viewModel.dismissClipboardPrompt()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text("立即解析")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.dismissClipboardPrompt() }) {
                            Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun DownloaderPanel(
    viewModel: DownloadViewModel,
    urlInput: String,
    parseState: ParseState,
    focusManager: androidx.compose.ui.focus.FocusManager,
    clipboardManager: ClipboardManager
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))

            // Branding banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                        RoundedCornerShape(24.dp)
                    )
                    .padding(24.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.DownloadForOffline, contentDescription = "Logo", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(36.dp))
                    }
                    Column {
                        Text(
                            text = "极速无水印下载",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "精简多线程极速断点续传引擎",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            // Typing Input card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                        RoundedCornerShape(20.dp)
                    ),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "手动输入视频地址",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { viewModel.setUrlInput(it) },
                        placeholder = { Text("粘贴抖音分享文案、短连接或标准链接", fontSize = 13.sp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("url_text_input"),
                        trailingIcon = {
                            if (urlInput.isNotEmpty()) {
                                IconButton(onClick = { viewModel.clearUrlInput() }) {
                                    Icon(Icons.Default.Clear, contentDescription = "清空输入")
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            focusManager.clearFocus()
                            viewModel.triggerParse(urlInput)
                        }),
                        maxLines = 4,
                        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Regular paste button
                        OutlinedButton(
                            onClick = {
                                try {
                                    val clip = clipboardManager.primaryClip
                                    if (clip != null && clip.itemCount > 0) {
                                        val text = clip.getItemAt(0).text?.toString() ?: ""
                                        if (text.isNotEmpty()) {
                                            viewModel.setUrlInput(text)
                                            Toast.makeText(context, "已粘贴", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        Toast.makeText(context, "剪贴板中没有内容", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "读取剪贴板受阻：${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = "粘贴")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("粘贴")
                        }

                        // Parse triggers button
                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                viewModel.triggerParse(urlInput)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("parse_confirm_button")
                        ) {
                            Icon(Icons.Default.QueryStats, contentDescription = "智能解析")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("开始解析")
                        }
                    }
                }
            }
        }

        item {
            // Parsing dynamic states
            AnimatedContent(
                targetState = parseState,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "ParsingTransitions"
            ) { state ->
                when (state) {
                    is ParseState.Idle -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("等待输入或检测...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                        }
                    }
                    is ParseState.Parsing -> {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                                    RoundedCornerShape(20.dp)
                                ),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(16.dp))
                                Text("正在极速解析抖音视频...")
                            }
                        }
                    }
                    is ParseState.Parsed -> {
                        ParsedVideoInfoCard(parsedInfo = state.info, viewModel = viewModel)
                    }
                    is ParseState.Error -> {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.error.copy(alpha = 0.25f),
                                    RoundedCornerShape(20.dp)
                                ),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.ErrorOutline, contentDescription = "错误", tint = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(state.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun ParsedVideoInfoCard(
    parsedInfo: ParsedVideoInfo,
    viewModel: DownloadViewModel
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                RoundedCornerShape(20.dp)
            )
            .testTag("parsed_info_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "解析结果",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Async image cover
                Box(
                    modifier = Modifier
                        .size(90.dp, 120.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Gray)
                ) {
                    AsyncImage(
                        model = parsedInfo.coverUrl,
                        contentDescription = "视频封面",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = parsedInfo.title,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )

                    Text(
                        text = "无水印高清源已就绪",
                        fontSize = 11.sp,
                        color = Color(0xFF81C784),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { viewModel.setPreviewVideo(parsedInfo.videoUrl, parsedInfo.title) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "预览")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("边看边下")
                }

                Button(
                    onClick = { viewModel.startNewDownload(parsedInfo) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("start_download_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Default.Download, contentDescription = "下载")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("高速下载")
                }
            }
        }
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryRecordsPanel(
    viewModel: DownloadViewModel,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    historyItems: List<DownloadItem>,
    isSelectionMode: Boolean,
    selectedItemIds: Set<Long>
) {
    val context = LocalContext.current
    val filteredItems = remember(searchQuery, historyItems) {
        if (searchQuery.trim().isEmpty()) {
            historyItems
        } else {
            historyItems.filter { it.title.contains(searchQuery, ignoreCase = true) }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Simple search and stats
        PaddingValues(horizontal = 16.dp).also {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("搜索本地视频标题", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "搜索", tint = MaterialTheme.colorScheme.primary) },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                ),
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "清空搜索")
                        }
                    }
                }
            )
        }

        if (filteredItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = "暂无记录",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.size(64.dp)
                    )
                    Text(
                        text = if (searchQuery.isNotEmpty()) "未找到匹配的本地视频记录" else "暂无下载记录",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredItems, key = { it.id }) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                if (selectedItemIds.contains(item.id)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                                RoundedCornerShape(16.dp)
                            )
                            .combinedClickable(
                                onClick = {
                                    if (isSelectionMode) {
                                        viewModel.toggleItemSelection(item.id)
                                    } else {
                                        // Play / preview completed item or show state info on downloading
                                        if (item.status == "COMPLETED") {
                                            viewModel.setPreviewVideo(item.localPath, item.title)
                                        } else {
                                            viewModel.togglePlayPause(item)
                                        }
                                    }
                                },
                                onLongClick = {
                                    if (!isSelectionMode) {
                                        viewModel.toggleSelectionMode()
                                        viewModel.toggleItemSelection(item.id)
                                    }
                                }
                            )
                            .testTag("history_item_${item.id}"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedItemIds.contains(item.id)) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isSelectionMode) {
                                Checkbox(
                                    checked = selectedItemIds.contains(item.id),
                                    onCheckedChange = { viewModel.toggleItemSelection(item.id) },
                                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                                )
                            }

                            // Cover Art (with play overlays if completed, or downloading spinner if active)
                            Box(
                                modifier = Modifier
                                    .size(70.dp, 90.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.DarkGray)
                            ) {
                                AsyncImage(
                                    model = item.coverUrl,
                                    contentDescription = "封面",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )

                                if (item.status == "COMPLETED") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.3f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.PlayCircleOutline, contentDescription = "播放", tint = Color.White, modifier = Modifier.size(28.dp))
                                    }
                                } else if (item.status == "DOWNLOADING") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.4f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                    }
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                // Download detail state row
                                when (item.status) {
                                    "COMPLETED" -> {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = "成功", tint = Color(0xFF81C784), modifier = Modifier.size(14.dp))
                                            Text("已下载 100%", fontSize = 11.sp, color = Color(0xFF81C784), fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            // Show local file size
                                            val file = File(item.localPath)
                                            val sizeStr = if (file.exists()) formatBytes(file.length()) else formatBytes(item.totalBytes)
                                            Text(sizeStr, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        // Completed actions (Share, Saved to Album, Delete)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            TextButton(
                                                onClick = { viewModel.exportToGallery(context, item) },
                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                                            ) {
                                                Icon(Icons.Default.Collections, contentDescription = "存相册", modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(2.dp))
                                                Text("导相册", fontSize = 11.sp)
                                            }

                                            TextButton(
                                                onClick = { viewModel.shareVideo(context, item) },
                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                                            ) {
                                                Icon(Icons.Default.Share, contentDescription = "分享", modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(2.dp))
                                                Text("分享", fontSize = 11.sp)
                                            }

                                            Spacer(modifier = Modifier.weight(1f))

                                            IconButton(
                                                onClick = { viewModel.deleteItem(item) },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(Icons.Default.DeleteOutline, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                    "DOWNLOADING", "PENDING", "PAUSED", "ERROR" -> {
                                        val percent = if (item.totalBytes > 0) (item.downloadedBytes * 100 / item.totalBytes).toInt() else 0
                                        val downloadingStr = if (item.status == "DOWNLOADING") "下载中" else if (item.status == "PAUSED") "已暂停" else if (item.status == "ERROR") "故障 (点击重试)" else "等待中..."

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("$downloadingStr $percent%", fontSize = 11.sp, color = if (item.status == "ERROR") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.weight(1f))
                                            Text("${formatBytes(item.downloadedBytes)} / ${formatBytes(item.totalBytes)}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))

                                        LinearProgressIndicator(
                                            progress = { if (item.totalBytes > 0) item.downloadedBytes.toFloat() / item.totalBytes else 0.0f },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(3.dp)),
                                            color = if (item.status == "PAUSED" || item.status == "ERROR") Color.Gray else MaterialTheme.colorScheme.primary,
                                            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                                        )

                                        Spacer(modifier = Modifier.height(8.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Pause/resume action
                                            IconButton(
                                                onClick = { viewModel.togglePlayPause(item) },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (item.status == "DOWNLOADING") Icons.Default.PauseCircleFilled else Icons.Default.PlayCircleFilled,
                                                    contentDescription = "暂停/开始",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }

                                            Text(
                                                text = if (item.status == "DOWNLOADING") "多线程连接中..." else "支持断点续传",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                            )

                                            Spacer(modifier = Modifier.weight(1f))

                                            IconButton(
                                                onClick = { viewModel.deleteItem(item) },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(Icons.Default.DeleteOutline, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VideoPreviewOverlay(
    url: String,
    title: String,
    onCloseEvent: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(300.dp)
            .testTag("video_preview_overlay"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black),
        elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.SlowMotionVideo, contentDescription = "播放中", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = title,
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onCloseEvent,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "关闭播放器", tint = Color.LightGray)
                }
            }

            // Inline Video Player components
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                VideoPlayer(videoUrl = url, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
fun VideoPlayer(videoUrl: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lastUrl = remember { mutableStateOf("") }
    
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            VideoView(ctx).apply {
                val mediaController = MediaController(ctx)
                mediaController.setAnchorView(this)
                setMediaController(mediaController)
                setVideoPath(videoUrl)
                setOnPreparedListener { mp ->
                    mp.isLooping = true
                    start()
                }
                setOnErrorListener { mp, what, extra ->
                    Toast.makeText(context, "无法播放在线源或文件格式不兼容", Toast.LENGTH_SHORT).show()
                    true
                }
            }
        },
        update = { videoView ->
            if (lastUrl.value != videoUrl) {
                lastUrl.value = videoUrl
                videoView.setVideoPath(videoUrl)
                videoView.start()
            }
        }
    )
}

// Utility byte formatting helper
fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0.0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format("%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

// Utility extension definitions to keep code compiling cleanly
val ColorScheme.textSecondaryVariant: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFFA5A6AF) else Color(0xFF6F707A)

val ColorScheme.onSurfaceVariantTint: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFF4C4D58) else Color(0xFFE5E6EB)

// Utility extensions clean
val selectAllFlag: Boolean get() = true
