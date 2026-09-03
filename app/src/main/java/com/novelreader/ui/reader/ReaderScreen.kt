package com.novelreader.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.novelreader.core.AppContainer
import com.novelreader.core.AppVmFactory
import com.novelreader.core.parser.SourcesRepository
import com.novelreader.data.NovelCache
import com.novelreader.model.Chapter
import com.novelreader.network.NetworkStatus
import com.novelreader.network.OfflineException
import com.novelreader.ui.ReaderBg
import com.novelreader.ui.ReaderFontType
import com.novelreader.ui.ReaderTts
import com.novelreader.ui.htmlToParagraphs
import com.novelreader.ui.palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Kotatsu-style reader (vertical/webtoon equivalent): continuous LazyColumn of
 * paragraphs, resume from HistoryEntity, debounced progress persistence,
 * persisted reader settings, TTS.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    container: AppContainer,
    sourceId: String,
    novelPath: String,
    chapterPath: String,
    novelTitle: String,
    onBack: () -> Unit,
    onOpenChapter: (chapterPath: String, novelTitle: String) -> Unit = { _, _ -> },
    onOpenCf: (String) -> Unit,
    vm: ReaderViewModel = viewModel(factory = AppVmFactory(container)),
) {
    val settings = container.settings
    var paragraphs by remember { mutableStateOf(listOf("Memuat chapter…")) }
    var chapterName by remember { mutableStateOf(novelTitle.ifBlank { "Chapter" }) }
    var fontSp by remember { mutableFloatStateOf(settings.readerFontSp) }
    var lineMul by remember { mutableFloatStateOf(settings.readerLineMul) }
    var bgMode by remember { mutableStateOf(settings.readerBg) }
    var fontType by remember { mutableStateOf(settings.readerFontType) }
    var alignJustify by remember { mutableStateOf(settings.readerJustify) }
    var showSettings by remember { mutableStateOf(false) }
    var showAi by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var pendingResumeScroll by remember { mutableStateOf<Float?>(null) }
    var originalParagraphs by remember { mutableStateOf(listOf<String>()) }
    var translated by remember { mutableStateOf(false) }

    val novelId = SourcesRepository.novelKey(sourceId, novelPath)
    val source = container.source(sourceId)
    val listState = rememberLazyListState()
    val pal = palette(bgMode)
    val context = LocalContext.current

    val tts = remember { ReaderTts(context) }
    val isPlayingTts by tts.isPlaying.collectAsState()
    val ttsIndex by tts.currentIndex.collectAsState()
    val aiProgress by vm.aiProgress.collectAsState()
    val aiLive by vm.aiLive.collectAsState()

    // paragraphs rendered while streaming: translated ones replace originals
    // as they arrive, so the chapter "fills in" without waiting for the end
    val displayedParagraphs = remember(paragraphs, aiLive) {
        if (aiLive.isEmpty()) paragraphs
        else paragraphs.mapIndexed { i, orig -> aiLive[i] ?: orig }
    }
    DisposableEffect(Unit) {
        onDispose {
            tts.shutdown()
        }
    }
    val scrollProgress by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val total = info.totalItemsCount
            if (total <= 1) 0f
            else {
                val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                (last.toFloat() / (total - 1).coerceAtLeast(1)).coerceIn(0f, 1f)
            }
        }
    }

    // persisted settings (write-back)
    LaunchedEffect(fontSp) { settings.readerFontSp = fontSp }
    LaunchedEffect(lineMul) { settings.readerLineMul = lineMul }
    LaunchedEffect(bgMode) { settings.readerBg = bgMode }
    LaunchedEffect(fontType) { settings.readerFontType = fontType }
    LaunchedEffect(alignJustify) { settings.readerJustify = alignJustify }

    // debounced progress save while reading
    LaunchedEffect(scrollProgress, chapterPath) {
        if (!loading && displayedParagraphs.size > 1) {
            vm.saveProgress(
                novelId, chapterPath, chapterName,
                scroll = scrollProgress,
                percent = scrollProgress,
                chaptersCount = 0,
            )
        }
    }

    // chapter load + resume
    LaunchedEffect(sourceId, chapterPath) {
        error = null
        // resume position for this novel (only when opening the last-read chapter)
        val resume = vm.resumePosition(novelId)
        pendingResumeScroll = if (resume?.first == chapterPath) resume.second.takeIf { it > 0.01f } else null
        val memCached = NovelCache.getChapter(chapterPath)
        if (memCached != null) {
            paragraphs = htmlToParagraphs(memCached)
            originalParagraphs = paragraphs
            loading = false
        } else {
            loading = true
            paragraphs = listOf("Memuat chapter…")
        }
        try {
            val content = withContext(Dispatchers.IO) {
                val offline = container.downloads.readChapterHtml(sourceId, novelPath, chapterPath)
                val body = when {
                    !offline.isNullOrBlank() -> offline
                    memCached != null -> memCached
                    else -> {
                        val html = source.getChapterContent(chapterPath)
                        NovelCache.putChapter(chapterPath, html)
                        html
                    }
                }
                htmlToParagraphs(body) to (!offline.isNullOrBlank())
            }
            paragraphs = content.first
            originalParagraphs = paragraphs
            chapterName = if (content.second) {
                "${novelTitle.ifBlank { "Chapter" }} · offline"
            } else {
                novelTitle.ifBlank { "Chapter" }
            }
            // record open in history (Kotatsu addOrUpdate on chapter open)
            val chapters = container.sourcesRepository.cachedChapters(novelId)
            vm.flushNow(
                novelId, chapterPath, chapterName,
                scroll = pendingResumeScroll ?: 0f,
                percent = pendingResumeScroll ?: 0f,
                chaptersCount = chapters.size,
            )
            // prefetch next chapter
            val idx = chapters.indexOfFirst { it.path == chapterPath }
            if (idx in 0 until chapters.size - 1) {
                val next = chapters[idx + 1]
                if (NovelCache.getChapter(next.path) == null) {
                    withContext(Dispatchers.IO) {
                        runCatching {
                            NovelCache.putChapter(next.path, source.getChapterContent(next.path))
                        }
                    }
                }
            }
        } catch (e: OfflineException) {
            // instant, friendly — no CF prompt, no retry storm
            error = NetworkStatus.OFFLINE_MESSAGE
            paragraphs = listOf(NetworkStatus.OFFLINE_MESSAGE)
        } catch (e: Exception) {
            val offline = withContext(Dispatchers.IO) {
                container.downloads.readChapterHtml(sourceId, novelPath, chapterPath)
            }
            if (!offline.isNullOrBlank()) {
                paragraphs = htmlToParagraphs(offline)
                chapterName = "${novelTitle.ifBlank { "Chapter" }} · offline"
            } else {
                error = e.message
                paragraphs = listOf(e.message ?: "Gagal memuat chapter.")
            }
        } finally {
            loading = false
        }
    }

    // chapter switch resets the AI translation view
    LaunchedEffect(chapterPath) {
        translated = false
    }

    fun toggleTranslate() {
        when {
            translated -> {
                paragraphs = originalParagraphs
                translated = false
            }
            aiProgress.running -> showAi = true // watch progress / cancel
            !vm.aiConfigured() -> showAi = true // configure provider first
            paragraphs.size > 1 -> {
                vm.translate(
                    novelId, chapterPath, paragraphs,
                    onLiveFinal = { result, _ ->
                        paragraphs = result
                        translated = true
                    },
                    onError = { showAi = true }, // surface the error in the sheet
                )
                showAi = false // watch the chapter fill in live
            }
        }
    }

    // jump to resume position once paragraphs are ready
    LaunchedEffect(paragraphs.size, pendingResumeScroll) {
        val r = pendingResumeScroll
        if (r != null && paragraphs.size > 1) {
            val target = (r * (paragraphs.size - 1)).toInt().coerceIn(0, paragraphs.size - 1)
            listState.scrollToItem(target)
            pendingResumeScroll = null
        }
    }

    LaunchedEffect(ttsIndex, isPlayingTts) {
        if (isPlayingTts && ttsIndex in paragraphs.indices) {
            listState.animateScrollToItem(ttsIndex)
        }
    }

    // prev/next chapter from Room chapter cache
    var prevNext by remember { mutableStateOf<Pair<Chapter?, Chapter?>>(null to null) }
    LaunchedEffect(novelId, chapterPath) {
        val chapters = runCatching { container.sourcesRepository.cachedChapters(novelId) }
            .getOrDefault(emptyList())
        val idx = chapters.indexOfFirst { it.path == chapterPath }
        val prev = if (idx > 0) chapters[idx - 1] else null
        val next = if (idx in 0 until chapters.size - 1) chapters[idx + 1] else null
        prevNext = prev to next
    }

    // flush pending debounced save before the back nav pops this screen —
    // the saveJob lives in viewModelScope and would be cancelled with it
    fun flushAndBack() {
        vm.flushNow(
            novelId, chapterPath, chapterName,
            scroll = scrollProgress,
            percent = scrollProgress,
            chaptersCount = 0,
        )
        onBack()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(pal.bg),
    ) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            novelTitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelMedium,
                            color = pal.muted,
                        )
                        Text(
                            chapterName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleSmall,
                            color = pal.text,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { flushAndBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = pal.text)
                    }
                },
                actions = {
                    IconButton(
                        enabled = !loading && paragraphs.isNotEmpty(),
                        onClick = {
                            val start = listState.firstVisibleItemIndex.coerceAtLeast(0)
                            tts.toggle(displayedParagraphs, start)
                        },
                    ) {
                        Icon(
                            if (isPlayingTts) Icons.AutoMirrored.Filled.VolumeOff
                            else Icons.AutoMirrored.Filled.VolumeUp,
                            if (isPlayingTts) "Stop TTS" else "Read Aloud",
                            tint = if (isPlayingTts) pal.accent else pal.text,
                        )
                    }
                    IconButton(
                        enabled = !loading && paragraphs.isNotEmpty(),
                        onClick = { toggleTranslate() },
                    ) {
                        Icon(
                            Icons.Default.Translate,
                            if (translated) "Tampilkan asli" else "AI Translate",
                            tint = if (translated || aiProgress.running) pal.accent else pal.text,
                        )
                    }
                    if (error != null && source.siteUrl != null) {
                        IconButton(onClick = { onOpenCf(source.siteUrl!!) }) {
                            Icon(Icons.Default.Security, "CF", tint = pal.accent)
                        }
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Tune, "Settings", tint = pal.text)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = pal.surface.copy(alpha = 0.92f),
                    titleContentColor = pal.text,
                    navigationIconContentColor = pal.text,
                    actionIconContentColor = pal.text,
                ),
            )

            LinearProgressIndicator(
                progress = { if (loading) 0f else scrollProgress.coerceAtLeast(0.02f) },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = pal.accent,
                trackColor = pal.muted.copy(alpha = 0.2f),
            )

            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = pal.accent, strokeWidth = 2.dp)
                        Spacer(Modifier.height(12.dp))
                        Text("Memuat…", color = pal.muted, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 20.dp, bottom = 48.dp),
                    verticalArrangement = Arrangement.spacedBy((fontSp * 0.55f).dp),
                ) {
                    if (error != null) {
                        item {
                            Surface(
                                color = pal.surface,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    error!!,
                                    Modifier.padding(16.dp),
                                    color = pal.muted,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                    items(displayedParagraphs.size) { i ->
                        Text(
                            text = displayedParagraphs[i],
                            color = pal.text,
                            fontSize = fontSp.sp,
                            lineHeight = (fontSp * lineMul).sp,
                            fontFamily = fontType.fontFamily,
                            letterSpacing = 0.15.sp,
                            textAlign = if (alignJustify) TextAlign.Justify else TextAlign.Left,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    item {
                        Spacer(Modifier.height(24.dp))
                        Text(
                            "— selesai —",
                            Modifier.fillMaxWidth(),
                            color = pal.muted,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Spacer(Modifier.height(16.dp))
                        val (prevChapter, nextChapter) = prevNext
                        if (prevChapter != null || nextChapter != null) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                if (prevChapter != null) {
                                    OutlinedButton(
                                        onClick = { onOpenChapter(prevChapter.path, novelTitle) },
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text("← Bab Sebelumnya", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                if (nextChapter != null) {
                                    Button(
                                        onClick = { onOpenChapter(nextChapter.path, novelTitle) },
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text("Bab Selanjutnya →", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showSettings) {
            ModalBottomSheet(
                onDismissRequest = { showSettings = false },
                containerColor = pal.surface,
                contentColor = pal.text,
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        "Pengaturan Tampilan Baca",
                        style = MaterialTheme.typography.titleMedium,
                        color = pal.text,
                    )
                    Text("Tema Latar", color = pal.muted, style = MaterialTheme.typography.labelLarge)
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ReaderBg.entries.forEach { mode ->
                            val p = palette(mode)
                            val selected = bgMode == mode
                            Surface(
                                onClick = { bgMode = mode },
                                shape = RoundedCornerShape(10.dp),
                                color = p.bg,
                                border = androidx.compose.foundation.BorderStroke(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) pal.accent else pal.muted.copy(alpha = 0.3f),
                                ),
                                modifier = Modifier.weight(1f).height(46.dp),
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        when (mode) {
                                            ReaderBg.Dark -> "Gelap"
                                            ReaderBg.Oled -> "OLED"
                                            ReaderBg.Nordic -> "Nordic"
                                            ReaderBg.Sepia -> "Sepia"
                                            ReaderBg.Light -> "Terang"
                                        },
                                        color = p.text,
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                            }
                        }
                    }
                    Text("Jenis Font", color = pal.muted, style = MaterialTheme.typography.labelLarge)
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ReaderFontType.entries.forEach { font ->
                            val selected = fontType == font
                            OutlinedButton(
                                onClick = { fontType = font },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f),
                                border = androidx.compose.foundation.BorderStroke(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) pal.accent else pal.muted.copy(alpha = 0.3f),
                                ),
                            ) {
                                Text(
                                    font.label,
                                    color = if (selected) pal.accent else pal.text,
                                    fontFamily = font.fontFamily,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Rataan Teks", color = pal.muted, style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { alignJustify = true }) {
                                Text(
                                    "Justify",
                                    color = if (alignJustify) pal.accent else pal.muted,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                            TextButton(onClick = { alignJustify = false }) {
                                Text(
                                    "Left",
                                    color = if (!alignJustify) pal.accent else pal.muted,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                    Text(
                        "Ukuran Font  ${fontSp.toInt()} sp",
                        color = pal.muted,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("A", color = pal.text, fontSize = 14.sp, fontFamily = fontType.fontFamily)
                        Slider(
                            value = fontSp,
                            onValueChange = { fontSp = it },
                            valueRange = 14f..28f,
                            steps = 6,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = pal.accent,
                                activeTrackColor = pal.accent,
                                inactiveTrackColor = pal.muted.copy(alpha = 0.3f),
                            ),
                        )
                        Text("A", color = pal.text, fontSize = 22.sp, fontFamily = fontType.fontFamily)
                    }
                    Text(
                        "Spasi Baris  ${"%.1f".format(lineMul)}×",
                        color = pal.muted,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Slider(
                        value = lineMul,
                        onValueChange = { lineMul = it },
                        valueRange = 1.4f..2.2f,
                        steps = 7,
                        colors = SliderDefaults.colors(
                            thumbColor = pal.accent,
                            activeTrackColor = pal.accent,
                            inactiveTrackColor = pal.muted.copy(alpha = 0.3f),
                        ),
                    )
                }
            }
        }
        if (showAi) {
            AiTranslateSheet(
                container = container,
                progress = aiProgress,
                translated = translated,
                palette = pal,
                liveCount = aiLive.size,
                totalParagraphs = paragraphs.size,
                onDismiss = { showAi = false },
                onTranslate = {
                    if (translated) {
                        paragraphs = originalParagraphs
                        translated = false
                    } else if (paragraphs.size > 1) {
                        vm.translate(
                            novelId, chapterPath, paragraphs,
                            onLiveFinal = { result, _ ->
                                paragraphs = result
                                translated = true
                            },
                            onError = { showAi = true },
                        )
                        showAi = false // watch the chapter fill in live
                    }
                },
                onCancel = { vm.cancelTranslate() },
            )
        }
    }
}

/**
 * AI Translate sheet: provider config (Gemini native / any OpenAI-compatible
 * endpoint) + per-batch progress. Fields write straight through to
 * [com.novelreader.core.prefs.AppSettings] so the next chapter uses them.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun AiTranslateSheet(
    container: AppContainer,
    progress: ReaderViewModel.AiProgress,
    translated: Boolean,
    palette: com.novelreader.ui.ReaderPalette,
    liveCount: Int,
    totalParagraphs: Int,
    onDismiss: () -> Unit,
    onTranslate: () -> Unit,
    onCancel: () -> Unit,
) {
    val settings = container.settings
    var provider by remember { mutableStateOf(settings.aiProvider) }
    var baseUrl by remember { mutableStateOf(settings.aiBaseUrl) }
    var apiKey by remember { mutableStateOf(settings.aiKey) }
    var model by remember { mutableStateOf(settings.aiModel) }
    var lang by remember { mutableStateOf(settings.aiTargetLang) }

    fun save() {
        settings.aiProvider = provider
        settings.aiBaseUrl = baseUrl
        settings.aiKey = apiKey
        settings.aiModel = model
        settings.aiTargetLang = lang
    }

    ModalBottomSheet(
        onDismissRequest = { onDismiss() },
        containerColor = palette.surface,
        contentColor = palette.text,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "AI Translate",
                style = MaterialTheme.typography.titleMedium,
                color = palette.text,
            )
            Text("Provider", color = palette.muted, style = MaterialTheme.typography.labelLarge)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    com.novelreader.translate.AiTranslationApi.PROVIDER_GEMINI to "Gemini API",
                    com.novelreader.translate.AiTranslationApi.PROVIDER_OPENAI to "OpenAI-compatible",
                ).forEach { (id, label) ->
                    val selected = provider == id
                    OutlinedButton(
                        onClick = { provider = id },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f),
                        border = androidx.compose.foundation.BorderStroke(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) palette.accent else palette.muted.copy(alpha = 0.3f),
                        ),
                    ) {
                        Text(
                            label,
                            color = if (selected) palette.accent else palette.text,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                        )
                    }
                }
            }
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it; save() },
                enabled = provider == com.novelreader.translate.AiTranslationApi.PROVIDER_OPENAI,
                label = { Text("Base URL") },
                placeholder = { Text("https://api.openai.com/v1") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it; save() },
                label = { Text(if (provider == com.novelreader.translate.AiTranslationApi.PROVIDER_GEMINI) "Gemini API key" else "API key") },
                placeholder = { Text("tempel API key di sini") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = model,
                onValueChange = { model = it; save() },
                label = { Text("Model") },
                placeholder = {
                    Text(
                        if (provider == com.novelreader.translate.AiTranslationApi.PROVIDER_GEMINI) "gemini-2.0-flash" else "gpt-4o-mini",
                    )
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = lang,
                onValueChange = { lang = it; save() },
                label = { Text("Bahasa target") },
                placeholder = { Text("Indonesian") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth(),
            )

            if (progress.running) {
                Text(
                    if (liveCount > 0) {
                        "Menerjemahkan… $liveCount/$totalParagraphs paragraf — hasil masuk bertahap di bawah"
                    } else {
                        "Menerjemahkan… ($totalParagraphs paragraf)"
                    },
                    color = palette.muted,
                    style = MaterialTheme.typography.labelMedium,
                )
                LinearProgressIndicator(
                    progress = {
                        if (totalParagraphs <= 0) 0f
                        else liveCount.toFloat() / totalParagraphs
                    },
                    modifier = Modifier.fillMaxWidth(),
                    color = palette.accent,
                    trackColor = palette.muted.copy(alpha = 0.2f),
                )
            } else if (progress.fromCache && translated) {
                Text(
                    "Hasil dari cache (model & bahasa sama).",
                    color = palette.muted,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            progress.error?.let { err ->
                Text(
                    err,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (translated) {
                OutlinedButton(onClick = onTranslate, modifier = Modifier.fillMaxWidth()) {
                    Text("Tampilkan Teks Asli")
                }
            } else if (progress.running) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    Text("Batalkan")
                }
            } else {
                Button(
                    onClick = onTranslate,
                    enabled = apiKey.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Terjemahkan Bab Ini")
                }
            }
            Text(
                "Hasil diterjemahkan disimpan per bab + bahasa + model. " +
                    "Layanan lokal (Ollama/LM Studio) via http:// tidak bisa diakses " +
                    "karena aplikasi memblokir cleartext.",
                color = palette.muted,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
