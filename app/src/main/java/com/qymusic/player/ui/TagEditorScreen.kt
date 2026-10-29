package com.qymusic.player.ui

import android.graphics.BitmapFactory
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qymusic.player.R
import com.qymusic.player.data.AudioTagWriter
import com.qymusic.player.data.ChineseConverter
import com.qymusic.player.data.EmbeddedTags
import com.qymusic.player.data.LxMusicSong
import com.qymusic.player.data.MetadataFetchService
import com.qymusic.player.data.MetadataFetchRequest
import com.qymusic.player.data.MusicDownloadRepository
import com.qymusic.player.data.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 抓取菜单动作。 */
private enum class TagFetchAction(val labelRes: Int) {
    INFO(R.string.tag_fetch_info),
    COVER(R.string.tag_fetch_cover),
    LYRICS(R.string.tag_fetch_lyrics),
    ALL(R.string.tag_fetch_all),
}

/** 保存结果。 */
private sealed interface SaveOutcome {
    data object Saved : SaveOutcome
    data object Unsupported : SaveOutcome
    data class Error(val message: String) : SaveOutcome
}

private const val SAVE_BUFFER_SIZE = 128 * 1024

/** 封面编辑状态：沿用原图 / 已替换字节 / 已删除。 */
private sealed interface CoverEdit {
    data object Unchanged : CoverEdit
    data class Replaced(val bytes: ByteArray, val mime: String) : CoverEdit
    data object Removed : CoverEdit
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagEditorScreen(
    track: Track,
    metadataService: MetadataFetchService,
    onBack: () -> Unit,
    onTagsSaved: (Track) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // ---- 基本信息（文本可随旋转保留） ----
    var title by rememberSaveable { mutableStateOf("") }
    var artist by rememberSaveable { mutableStateOf("") }
    var album by rememberSaveable { mutableStateOf("") }
    var genre by rememberSaveable { mutableStateOf("") }
    var year by rememberSaveable { mutableStateOf("") }
    var trackNumber by rememberSaveable { mutableStateOf("") }
    var lyrics by rememberSaveable { mutableStateOf("") }

    var loading by rememberSaveable { mutableStateOf(true) }
    var loadUnsupported by rememberSaveable { mutableStateOf(false) }
    var loadError by rememberSaveable { mutableStateOf<String?>(null) }
    var originalHasCover by rememberSaveable { mutableStateOf(false) }
    var originalHasLyrics by rememberSaveable { mutableStateOf(false) }

    // ---- 封面与运行时状态（旋转后重读） ----
    var coverBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var coverEdit by remember { mutableStateOf<CoverEdit>(CoverEdit.Unchanged) }

    var saving by rememberSaveable { mutableStateOf(false) }
    var fetchingAction by rememberSaveable { mutableStateOf<String?>(null) }
    var showFetchMenu by remember { mutableStateOf(false) }
    var pendingFetch by remember { mutableStateOf<TagFetchAction?>(null) }
    val hasAnyContent = title.isNotBlank() || artist.isNotBlank() ||
        album.isNotBlank() || lyrics.isNotBlank() || originalHasCover

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                }.onSuccess { bytes ->
                    if (bytes.size < 64) {
                        snackbarHostState.showSnackbar(
                            context.getString(R.string.tag_cover_invalid),
                        )
                    } else {
                        coverEdit = CoverEdit.Replaced(bytes, "image/jpeg")
                        coverBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }
                }
            }
        }
    }

    fun decodeCover(bytes: ByteArray): android.graphics.Bitmap? = runCatching {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()

    // ---- 初次读取标签 ----
    LaunchedEffect(track.id) {
        loading = true
        loadError = null
        loadUnsupported = false
        val result = runCatching {
            withContext(Dispatchers.IO) {
                val input = context.contentResolver.openInputStream(track.uri)
                    ?: throw IllegalStateException(context.getString(R.string.tag_error_read))
                AudioTagWriter.read(input, track.extension)
            }
        }.getOrElse {
            loading = false
            loadError = it.message ?: context.getString(R.string.tag_error_read)
            return@LaunchedEffect
        }
        when {
            result.unsupported -> loadUnsupported = true
            result.error != null -> loadError = result.error
            result.tags != null -> {
                title = result.tags.title
                artist = result.tags.artist
                album = result.tags.album
                genre = result.tags.genre
                year = result.tags.year
                trackNumber = result.tags.trackNumber
                lyrics = result.tags.lyrics.orEmpty()
                originalHasCover = result.tags.coverBytes != null
                originalHasLyrics = !result.tags.lyrics.isNullOrBlank()
                coverBitmap = result.tags.coverBytes?.let(::decodeCover)
            }
        }
        loading = false
    }

    // ---- 抓取 ----
    fun songLike(): LxMusicSong = LxMusicSong(
        id = track.id,
        sourceKey = "",
        sourceName = "",
        title = title.ifBlank { track.title },
        artist = artist.ifBlank { track.artist },
        album = album.ifBlank { track.album },
        durationMs = track.durationMs,
        coverUrl = null,
        rawJson = "{}",
    )

    fun runFetch(action: TagFetchAction) {
        fetchingAction = action.name
        scope.launch {
            val song = songLike()
            val result: String = withContext(Dispatchers.IO) {
                when (action) {
                    TagFetchAction.INFO -> {
                        val info = metadataService.fetchSongInfo(song)
                        if (info == null) {
                            context.getString(R.string.tag_fetch_no_result)
                        } else {
                            title = info.title.ifBlank { title }
                            artist = info.artist.ifBlank { artist }
                            album = info.album.ifBlank { album }
                            year = info.date.take(4).ifBlank { year }
                            trackNumber = info.trackNumber.ifBlank { trackNumber }
                            if (info.coverUrl.isNotBlank()) {
                                com.qymusic.player.data.downloadImage(info.coverUrl)
                                    ?.let { bytes ->
                                        coverEdit = CoverEdit.Replaced(
                                            bytes,
                                            "image/jpeg",
                                        )
                                        coverBitmap = decodeCover(bytes)
                                    }
                            }
                            context.getString(R.string.tag_fetch_done_from, info.sourceName)
                        }
                    }

                    TagFetchAction.COVER -> {
                        val cover = metadataService.fetchMetadata(
                            song = song,
                            request = MetadataFetchRequest(
                                wantLyrics = false,
                                wantCover = true,
                            ),
                        ).cover
                        if (cover == null) {
                            context.getString(R.string.tag_cover_not_found)
                        } else {
                            coverEdit = CoverEdit.Replaced(cover.bytes, cover.mimeType)
                            coverBitmap = decodeCover(cover.bytes)
                            context.getString(R.string.tag_fetch_done_from, cover.sourceName)
                        }
                    }

                    TagFetchAction.LYRICS -> {
                        val fetched = metadataService.fetchMetadata(
                            song = song,
                            request = MetadataFetchRequest(
                                wantLyrics = true,
                                wantCover = false,
                            ),
                        ).lyrics
                        if (fetched == null) {
                            context.getString(R.string.tag_lyrics_not_found)
                        } else {
                            lyrics = fetched.lrcText
                            context.getString(R.string.tag_fetch_done_from, fetched.sourceName)
                        }
                    }

                    TagFetchAction.ALL -> {
                        val bundle = metadataService.fetchMetadata(
                            song = song,
                            request = MetadataFetchRequest(
                                wantLyrics = true,
                                wantCover = true,
                            ),
                        )
                        val parts = mutableListOf<String>()
                        bundle.lyrics?.let {
                            lyrics = it.lrcText
                            parts.add(context.getString(R.string.tag_field_lyrics))
                        }
                        bundle.cover?.let {
                            coverEdit = CoverEdit.Replaced(it.bytes, it.mimeType)
                            coverBitmap = decodeCover(it.bytes)
                            parts.add(context.getString(R.string.tag_field_cover))
                        }
                        if (parts.isEmpty()) {
                            context.getString(R.string.tag_fetch_no_result)
                        } else {
                            context.getString(
                                R.string.tag_fetch_done_fields,
                                parts.joinToString("、"),
                            )
                        }
                    }
                }
            }
            fetchingAction = null
            snackbarHostState.showSnackbar(result)
        }
    }

    // ---- 保存 ----
    fun save() {
        saving = true
        scope.launch {
            val outcome: SaveOutcome = withContext(Dispatchers.IO) {
                try {
                    val original = context.contentResolver.openInputStream(track.uri)
                        ?: return@withContext SaveOutcome.Error(
                            context.getString(R.string.tag_error_read),
                        )
                    val tags = EmbeddedTags(
                        title = title.trim(),
                        artist = artist.trim(),
                        album = album.trim(),
                        genre = genre.trim(),
                        year = year.trim(),
                        trackNumber = trackNumber.trim(),
                        lyrics = lyrics.takeIf { it.isNotBlank() },
                        coverBytes = (coverEdit as? CoverEdit.Replaced)?.bytes,
                        coverMime = (coverEdit as? CoverEdit.Replaced)?.mime,
                        removeCover = coverEdit is CoverEdit.Removed,
                        removeLyrics = originalHasLyrics && lyrics.isBlank(),
                    )
                    // 先在临时文件上完成标签改写，成功后才打开原文件的输出流——
                    // openOutputStream("w") 一旦打开就会清空原文件，
                    // 顺序错了的话，改写阶段任何异常都会把原文件毁掉。
                    original.use { input ->
                        val (temp, report) = AudioTagWriter.editToTemp(
                            input,
                            tags,
                            track.extension,
                        )
                        if (report.unsupportedFormat) {
                            return@use SaveOutcome.Unsupported
                        }
                        try {
                            val output = context.contentResolver.openOutputStream(track.uri, "w")
                                ?: return@use SaveOutcome.Error(
                                    context.getString(R.string.tag_error_write),
                                )
                            output.use { out ->
                                temp.inputStream().buffered(SAVE_BUFFER_SIZE).use { buffered ->
                                    buffered.copyTo(out, SAVE_BUFFER_SIZE)
                                }
                            }
                            if (report.wroteAnything) {
                                SaveOutcome.Saved
                            } else {
                                SaveOutcome.Error(context.getString(R.string.tag_error_write))
                            }
                        } finally {
                            temp.delete()
                        }
                    }
                } catch (error: Exception) {
                    Log.e(
                        "QYTagEditor",
                        "标签保存失败: ${track.title} (${track.extension})",
                        error,
                    )
                    SaveOutcome.Error(
                        error.message?.takeIf { it.isNotBlank() }
                            ?: context.getString(R.string.tag_error_write),
                    )
                }
            }
            saving = false
            when (outcome) {
                SaveOutcome.Saved -> {
                    onTagsSaved(track)
                    snackbarHostState.showSnackbar(context.getString(R.string.tag_saved))
                }
                SaveOutcome.Unsupported ->
                    snackbarHostState.showSnackbar(context.getString(R.string.tag_error_unsupported))
                is SaveOutcome.Error ->
                    snackbarHostState.showSnackbar(outcome.message)
            }
        }
    }

    BackHandler(enabled = saving || fetchingAction != null) { /* 忙碌时拦截返回 */ }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
                Button(
                    onClick = { save() },
                    enabled = !loading && !saving && !loadUnsupported &&
                        fetchingAction == null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    if (saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(text = stringResource(R.string.tag_save))
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.tag_editor_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
                Box {
                    if (fetchingAction != null) {
                        // 抓取中：入口图标变成加载圈，不再展开菜单
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(24.dp)
                                .padding(2.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        IconButton(onClick = { showFetchMenu = true }) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = stringResource(R.string.tag_fetch_menu),
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = showFetchMenu,
                        onDismissRequest = { showFetchMenu = false },
                    ) {
                        TagFetchAction.entries.forEach { action ->
                            DropdownMenuItem(
                                text = { Text(text = stringResource(action.labelRes)) },
                                onClick = {
                                    showFetchMenu = false
                                    // 已有内容时先确认覆盖
                                    if (hasAnyContent) {
                                        pendingFetch = action
                                    } else {
                                        runFetch(action)
                                    }
                                },
                            )
                        }
                    }
                }
            }

            fetchingAction?.let { actionName ->
                val action = TagFetchAction.entries.firstOrNull { it.name == actionName }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(
                            text = action?.let {
                                stringResource(
                                    R.string.tag_fetch_running,
                                    stringResource(it.labelRes),
                                )
                            } ?: stringResource(R.string.tag_fetch_menu),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            when {
                loading -> Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }

                loadUnsupported -> TagEditorMessage(
                    text = stringResource(
                        R.string.tag_error_unsupported_format,
                        track.extension,
                    ),
                )

                loadError != null -> TagEditorMessage(text = loadError!!)

                else -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    TagSection(title = stringResource(R.string.tag_section_basic)) {
                        TagField(
                            label = stringResource(R.string.tag_field_title),
                            value = title,
                            onValueChange = { title = it },
                        )
                        TagField(
                            label = stringResource(R.string.tag_field_artist),
                            value = artist,
                            onValueChange = { artist = it },
                        )
                        TagField(
                            label = stringResource(R.string.tag_field_album),
                            value = album,
                            onValueChange = { album = it },
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TagField(
                                label = stringResource(R.string.tag_field_genre),
                                value = genre,
                                onValueChange = { genre = it },
                                modifier = Modifier.weight(1f),
                            )
                            TagField(
                                label = stringResource(R.string.tag_field_year),
                                value = year,
                                onValueChange = { year = it.take(4).filter(Char::isDigit) },
                                modifier = Modifier.width(110.dp),
                            )
                        }
                        TagField(
                            label = stringResource(R.string.tag_field_track),
                            value = trackNumber,
                            onValueChange = { trackNumber = it.filter(Char::isDigit).take(4) },
                            modifier = Modifier.width(160.dp),
                        )
                    }

                    TagSection(title = stringResource(R.string.tag_section_cover)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(120.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                        shape = RoundedCornerShape(10.dp),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                val bitmap = coverBitmap
                                when {
                                    bitmap != null -> Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )

                                    else -> Icon(
                                        imageVector = Icons.Rounded.Image,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { imagePicker.launch("image/*") }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Edit,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(text = stringResource(R.string.tag_cover_change))
                                }
                                OutlinedButton(
                                    onClick = {
                                        coverEdit = CoverEdit.Removed
                                        coverBitmap = null
                                    },
                                    enabled = coverBitmap != null,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteOutline,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(text = stringResource(R.string.tag_cover_remove))
                                }
                            }
                        }
                    }

                    TagSection(title = stringResource(R.string.tag_section_lyrics)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.tag_lyrics_hint),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedButton(onClick = {
                                lyrics = ChineseConverter.convert(
                                    context,
                                    lyrics,
                                    ChineseConverter.Direction.S2T,
                                )
                            }) {
                                Text(text = stringResource(R.string.tag_lyrics_s2t))
                            }
                            OutlinedButton(onClick = {
                                lyrics = ChineseConverter.convert(
                                    context,
                                    lyrics,
                                    ChineseConverter.Direction.T2S,
                                )
                            }) {
                                Text(text = stringResource(R.string.tag_lyrics_t2s))
                            }
                        }
                        OutlinedTextField(
                            value = lyrics,
                            onValueChange = { lyrics = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                            textStyle = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                            ),
                            placeholder = {
                                Text(text = stringResource(R.string.tag_lyrics_placeholder))
                            },
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }

    // 抓取覆盖确认
    pendingFetch?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingFetch = null },
            title = { Text(text = stringResource(R.string.tag_fetch_confirm_title)) },
            text = { Text(text = stringResource(R.string.tag_fetch_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingFetch = null
                    runFetch(action)
                }) {
                    Text(text = stringResource(R.string.tag_fetch_confirm_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingFetch = null }) {
                    Text(text = stringResource(R.string.close))
                }
            },
        )
    }
}

@Composable
private fun TagSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            content()
        }
    }
}

@Composable
private fun TagField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = label) },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun TagEditorMessage(text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Rounded.Refresh,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
