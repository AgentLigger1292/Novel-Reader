package com.novelreader.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.novelreader.NovelApp
import com.novelreader.data.HistoryEntity
import com.novelreader.data.LibraryEntity
import com.novelreader.data.libraryKey
import com.novelreader.model.Novel
import com.novelreader.model.NovelDetail
import com.novelreader.network.CfChallengeException
import com.novelreader.network.CoverLoader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.novelreader.network.SessionBusyException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    app: NovelApp,
    onOpenNovel: (String, String) -> Unit,
    onOpenCf: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var novels by remember { mutableStateOf<List<Novel>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var sourceId by remember { mutableStateOf(app.selectedSourceId) }
    var sourceMenu by remember { mutableStateOf(false) }
    var reload by remember { mutableStateOf(0) }
    // bump when a cover is prefetched → grid re-reads cache files
    var coverTick by remember { mutableIntStateOf(0) }
    val cookieGen by app.cookieGeneration.collectAsState()
    val source = app.sources[sourceId] ?: app.selectedSource
    val context = LocalContext.current

    // debounce search so WebView isn't spammed per keystroke
    LaunchedEffect(query, sourceId, reload, cookieGen) {
        app.selectedSourceId = sourceId
        val q = query.trim()
        if (q.isNotEmpty() && q.length < 2) {
            // wait for more chars; don't thrash session WebView
            return@LaunchedEffect
        }
        if (q.isNotEmpty()) delay(550)
        loading = true
        error = null
        try {
            novels = withContext(Dispatchers.IO) {
                if (q.isEmpty()) source.getPopular(1) else source.search(q, 1)
            }
            if (novels.isEmpty() && sourceId == "bacalightnovel") {
                error = if (q.isEmpty()) {
                    "0 novel. CF dulu (shield), atau cek: adb logcat -s BLN"
                } else {
                    "Tidak ketemu untuk \"$q\". Coba kata lain / pastikan CF sudah Done."
                }
            } else if (novels.isNotEmpty()) {
                // after CF cookies exist: OkHttp then WebView fallback; refresh grid as each lands
                CoverLoader.prefetch(
                    context,
                    novels.map { it.coverUrl },
                ) { coverTick++ }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: CfChallengeException) {
            novels = emptyList()
            error = "Cloudflare: buka CF, selesaikan challenge, tap Done, lalu refresh."
        } catch (e: SessionBusyException) {
            novels = emptyList()
            error = e.message
        } catch (e: Exception) {
            novels = emptyList()
            val msg = e.message.orEmpty()
            error = when {
                msg.contains("busy", ignoreCase = true) ->
                    "Jaringan sibuk — tunggu sebentar lalu refresh."
                msg.contains("timeout", ignoreCase = true) ->
                    "Timeout. Pastikan CF Done, lalu refresh."
                else -> msg.ifBlank { e.toString() }
            }
            android.util.Log.e("BLN", "browse error", e)
        } finally {
            loading = false
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ExposedDropdownMenuBox(expanded = sourceMenu, onExpandedChange = { sourceMenu = it }) {
            OutlinedTextField(
                value = source.name,
                onValueChange = {},
                readOnly = true,
                label = { Text("Source") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(sourceMenu) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
            )
            DropdownMenu(expanded = sourceMenu, onDismissRequest = { sourceMenu = false }) {
                app.sources.values.forEach { s ->
                    DropdownMenuItem(
                        text = { Text(s.name) },
                        onClick = {
                            sourceId = s.id
                            sourceMenu = false
                        },
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (source.siteUrl != null) {
                Button(onClick = { onOpenCf(source.siteUrl!!) }) {
                    Icon(Icons.Default.Security, null)
                    Text(" CF", Modifier.padding(start = 4.dp))
                }
            }
            IconButton(onClick = { reload++ }) {
                Icon(Icons.Default.Refresh, "Reload")
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Cari novel") },
            placeholder = { Text("min. 2 huruf, tunggu sebentar…") },
        )
        when {
            loading -> Text(if (query.isBlank()) "Loading…" else "Mencari…")
            query.isNotBlank() && query.trim().length < 2 -> Text("Ketik min. 2 huruf untuk search")
            error != null -> {
                Text(error!!)
                if (source.siteUrl != null) {
                    Button(onClick = { onOpenCf(source.siteUrl!!) }) {
                        Text("Open site (manual CF)")
                    }
                }
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            gridItems(novels, key = { it.path }) { n ->
                NovelGridCard(
                    title = n.title,
                    coverUrl = n.coverUrl,
                    refreshKey = coverTick,
                    onClick = { onOpenNovel(n.sourceId, n.path) },
                )
            }
        }
    }
}

@Composable
fun LibraryScreen(app: NovelApp, onOpenNovel: (String, String) -> Unit) {
    val items by app.store.library.collectAsState()
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Library kosong", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize().padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        gridItems(items, key = { it.key }) { e ->
            NovelGridCard(
                title = e.title,
                coverUrl = e.coverUrl,
                refreshKey = 0,
                onClick = { onOpenNovel(e.sourceId, e.path) },
            )
        }
    }
}

/** Cover box + title under it. */
@Composable
fun NovelGridCard(
    title: String,
    coverUrl: String?,
    refreshKey: Int = 0,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val imageLoader = remember { CoverLoader.get(context) }
    // re-resolve model when prefetch writes cache (refreshKey)
    val model = remember(coverUrl, refreshKey) { CoverLoader.request(context, coverUrl) }

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.70f)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                if (model != null) {
                    SubcomposeAsyncImage(
                        model = model,
                        imageLoader = imageLoader,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        loading = {
                            CircularProgressIndicator(
                                Modifier.padding(20.dp),
                                strokeWidth = 2.dp,
                            )
                        },
                        error = {
                            Icon(
                                Icons.AutoMirrored.Filled.MenuBook,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                            )
                        },
                    )
                } else {
                    Icon(
                        Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            Text(
                text = title,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
fun HistoryScreen(
    app: NovelApp,
    onOpenChapter: (sourceId: String, novelPath: String, chapterPath: String, novelTitle: String) -> Unit,
) {
    val items by app.store.history.collectAsState()
    LazyColumn(Modifier.fillMaxSize()) {
        items(items, key = { it.key }) { e ->
            ListItem(
                headlineContent = { Text(e.novelTitle) },
                supportingContent = { Text(e.chapterName) },
                modifier = Modifier.clickable {
                    onOpenChapter(e.sourceId, e.novelPath, e.chapterPath, e.novelTitle)
                },
            )
        }
    }
}

@Composable
fun DownloadsScreen(
    app: NovelApp,
    onOpenNovel: (String, String) -> Unit,
) {
    val items by app.downloads.entries.collectAsState()
    val scope = rememberCoroutineScope()
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "Belum ada unduhan.\nBuka novel → ikon Download.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(items, key = { it.key }) { e ->
            ListItem(
                headlineContent = { Text(e.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                supportingContent = {
                    Text("${e.downloadedCount}/${e.chapterCount} chapter · offline")
                },
                leadingContent = {
                    Icon(Icons.Default.Download, null, tint = MaterialTheme.colorScheme.primary)
                },
                trailingContent = {
                    IconButton(onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                app.downloads.delete(e.sourceId, e.novelPath)
                            }
                        }
                    }) {
                        Icon(Icons.Default.Delete, "Hapus")
                    }
                },
                modifier = Modifier.clickable { onOpenNovel(e.sourceId, e.novelPath) },
            )
            HorizontalDivider()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovelDetailScreen(
    app: NovelApp,
    sourceId: String,
    path: String,
    preferOffline: Boolean = false,
    onBack: () -> Unit,
    onOpenChapter: (chapterPath: String, novelTitle: String) -> Unit,
    onOpenCf: (String) -> Unit,
) {
    var detail by remember { mutableStateOf<NovelDetail?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var inLib by remember { mutableStateOf(false) }
    var newestFirst by remember { mutableStateOf(false) }
    var descExpanded by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var dlDone by remember { mutableIntStateOf(0) }
    var dlTotal by remember { mutableIntStateOf(0) }
    var dlChapter by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val source = app.sources[sourceId] ?: app.selectedSource
    val key = libraryKey(sourceId, path)
    val dlEntries by app.downloads.entries.collectAsState()
    val dlEntry = dlEntries.find { it.key == key }

    LaunchedEffect(sourceId, path, preferOffline) {
        error = null
        val cached = com.novelreader.data.NovelCache.getDetail(key)
        if (cached != null) {
            detail = cached
            loading = false
        } else {
            loading = true
        }
        try {
            val offline = withContext(Dispatchers.IO) {
                app.downloads.readNovelDetail(sourceId, path)
            }
            if (preferOffline && offline != null) {
                detail = offline
            } else {
                try {
                    val fresh = withContext(Dispatchers.IO) { source.getNovel(path) }
                    detail = fresh
                    com.novelreader.data.NovelCache.putDetail(key, fresh)
                } catch (e: Exception) {
                    if (offline != null) {
                        detail = offline
                    } else if (cached == null) {
                        throw e
                    }
                }
            }
            inLib = app.store.isInLibrary(key)
        } catch (e: CfChallengeException) {
            if (detail == null) error = "Cloudflare challenge"
        } catch (e: SessionBusyException) {
            if (detail == null) error = e.message
        } catch (e: Exception) {
            if (detail == null) error = e.message ?: e.toString()
        } finally {
            loading = false
        }
    }

    val chapters = remember(detail, newestFirst) {
        val list = detail?.chapters.orEmpty()
        if (newestFirst) list.asReversed() else list
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    detail?.novel?.title ?: "…",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
            },
            actions = {
                if (source.siteUrl != null) {
                    IconButton(onClick = { onOpenCf(source.siteUrl!!) }) {
                        Icon(Icons.Default.Security, "CF")
                    }
                }
                IconButton(
                    enabled = !downloading && detail != null,
                    onClick = {
                        val d = detail ?: return@IconButton
                        scope.launch {
                            downloading = true
                            dlDone = 0
                            dlTotal = d.chapters.size
                            try {
                                app.downloads.downloadAll(source, d) { done, total, name ->
                                    dlDone = done
                                    dlTotal = total
                                    dlChapter = name
                                }
                            } catch (e: Exception) {
                                error = "Download gagal: ${e.message}"
                            } finally {
                                downloading = false
                            }
                        }
                    },
                ) {
                    Icon(
                        if (dlEntry != null && dlEntry.downloadedCount > 0) {
                            Icons.Default.CheckCircle
                        } else {
                            Icons.Default.Download
                        },
                        "Download",
                    )
                }
                IconButton(onClick = {
                    val d = detail ?: return@IconButton
                    scope.launch {
                        if (inLib) {
                            app.store.deleteLibrary(key)
                            inLib = false
                        } else {
                            app.store.upsertLibrary(
                                LibraryEntity(
                                    key = key,
                                    sourceId = sourceId,
                                    path = path,
                                    title = d.novel.title,
                                    author = d.novel.author,
                                    coverUrl = d.novel.coverUrl,
                                ),
                            )
                            inLib = true
                        }
                    }
                }) {
                    Icon(
                        if (inLib) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        "Library",
                    )
                }
            },
        )

        when {
            loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                }
            }
            error != null -> {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(error!!)
                    if (source.siteUrl != null) {
                        Button(onClick = { onOpenCf(source.siteUrl!!) }) {
                            Text("Open site (manual CF)")
                        }
                    }
                }
            }
            detail != null -> {
                val d = detail!!
                Column(Modifier.fillMaxSize()) {
                    if (downloading) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(
                                "Mengunduh $dlDone/$dlTotal — $dlChapter",
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            LinearProgressIndicator(
                                progress = {
                                    if (dlTotal <= 0) 0f
                                    else dlDone.toFloat() / dlTotal.toFloat()
                                },
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                            )
                        }
                    } else if (dlEntry != null && dlEntry.downloadedCount > 0) {
                        Text(
                            "Offline: ${dlEntry.downloadedCount}/${dlEntry.chapterCount} chapter",
                            Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (!d.novel.author.isNullOrBlank()) {
                            Text(
                                d.novel.author!!,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        val desc = d.novel.description.orEmpty()
                        if (desc.isNotBlank()) {
                            Text(
                                text = if (descExpanded || desc.length <= 180) {
                                    desc
                                } else {
                                    desc.take(180).trimEnd() + "…"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = if (descExpanded) Int.MAX_VALUE else 4,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (desc.length > 180) {
                                Text(
                                    if (descExpanded) "Sembunyikan" else "Selengkapnya",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.clickable { descExpanded = !descExpanded },
                                )
                            }
                        }
                        if (chapters.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        val first = if (newestFirst) chapters.last() else chapters.first()
                                        onOpenChapter(first.path, d.novel.title)
                                    },
                                ) { Text("Mulai Ch.1") }
                                OutlinedButton(
                                    onClick = {
                                        val last = if (newestFirst) chapters.first() else chapters.last()
                                        onOpenChapter(last.path, d.novel.title)
                                    },
                                ) { Text("Terbaru") }
                            }
                        }
                    }

                    HorizontalDivider()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "${chapters.size} chapter",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                        TextButton(onClick = { newestFirst = !newestFirst }) {
                            Icon(
                                if (newestFirst) Icons.Default.ArrowDownward
                                else Icons.Default.ArrowUpward,
                                contentDescription = null,
                            )
                            Text(
                                if (newestFirst) " Baru→Lama" else " Lama→Baru",
                            )
                        }
                    }
                    HorizontalDivider()

                    LazyColumn(Modifier.fillMaxSize()) {
                        items(chapters, key = { it.path }) { ch ->
                            val numLabel = ch.number?.let { n ->
                                if (n == n.toLong().toFloat()) n.toLong().toString() else n.toString()
                            }
                            ListItem(
                                headlineContent = {
                                    Text(
                                        ch.name,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                leadingContent = if (numLabel != null) {
                                    {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                        ) {
                                            Text(
                                                numLabel,
                                                Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            )
                                        }
                                    }
                                } else null,
                                modifier = Modifier.clickable {
                                    onOpenChapter(ch.path, d.novel.title)
                                },
                            )
                            HorizontalDivider(
                                Modifier.padding(start = 72.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    app: NovelApp,
    sourceId: String,
    novelPath: String,
    chapterPath: String,
    novelTitle: String,
    onBack: () -> Unit,
    onOpenChapter: (chapterPath: String, novelTitle: String) -> Unit = { _, _ -> },
    onOpenCf: (String) -> Unit,
) {
    var paragraphs by remember { mutableStateOf(listOf("Loading…")) }
    var chapterName by remember { mutableStateOf(novelTitle.ifBlank { "Chapter" }) }
    var fontSp by remember { mutableFloatStateOf(18f) }
    var lineMul by remember { mutableFloatStateOf(1.7f) }
    var bgMode by remember { mutableStateOf(ReaderBg.Dark) }
    var fontType by remember { mutableStateOf(ReaderFontType.Serif) }
    var alignJustify by remember { mutableStateOf(true) }
    var showSettings by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    val source = app.sources[sourceId] ?: app.selectedSource
    val histKey = libraryKey(sourceId, novelPath)
    val listState = rememberLazyListState()
    val pal = palette(bgMode)

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

    LaunchedEffect(sourceId, chapterPath) {
        error = null
        val memCached = com.novelreader.data.NovelCache.getChapter(chapterPath)
        if (memCached != null) {
            paragraphs = htmlToParagraphs(memCached)
            loading = false
        } else {
            loading = true
            paragraphs = listOf("Memuat chapter…")
        }
        try {
            val content = withContext(Dispatchers.IO) {
                val offline = app.downloads.readChapterHtml(sourceId, novelPath, chapterPath)
                val body = if (!offline.isNullOrBlank()) {
                    offline
                } else if (memCached != null) {
                    memCached
                } else {
                    val html = source.getChapterContent(chapterPath)
                    com.novelreader.data.NovelCache.putChapter(chapterPath, html)
                    html
                }
                htmlToParagraphs(body) to (!offline.isNullOrBlank())
            }
            paragraphs = content.first
            chapterName = if (content.second) {
                "${novelTitle.ifBlank { "Chapter" }} · offline"
            } else {
                novelTitle.ifBlank { "Chapter" }
            }
            app.store.upsertHistory(
                HistoryEntity(
                    key = histKey,
                    sourceId = sourceId,
                    novelPath = novelPath,
                    novelTitle = novelTitle,
                    chapterPath = chapterPath,
                    chapterName = novelTitle.ifBlank { "Chapter" },
                ),
            )
            // Pre-fetch next chapter in background for instant reading
            val cachedDetail = com.novelreader.data.NovelCache.getDetail(histKey)
            if (cachedDetail != null) {
                val idx = cachedDetail.chapters.indexOfFirst { it.path == chapterPath }
                if (idx >= 0 && idx + 1 < cachedDetail.chapters.size) {
                    val nextChapterPath = cachedDetail.chapters[idx + 1].path
                    if (com.novelreader.data.NovelCache.getChapter(nextChapterPath) == null) {
                        withContext(Dispatchers.IO) {
                            runCatching {
                                val nextHtml = source.getChapterContent(nextChapterPath)
                                com.novelreader.data.NovelCache.putChapter(nextChapterPath, nextHtml)
                            }
                        }
                    }
                }
            }
        } catch (e: CfChallengeException) {
            // try offline only
            val offline = withContext(Dispatchers.IO) {
                app.downloads.readChapterHtml(sourceId, novelPath, chapterPath)
            }
            if (!offline.isNullOrBlank()) {
                paragraphs = htmlToParagraphs(offline)
                chapterName = "${novelTitle.ifBlank { "Chapter" }} · offline"
            } else {
                error = "Cloudflare challenge"
                paragraphs = listOf("Cloudflare memblokir. Tap shield, selesaikan CF, buka chapter lagi.")
            }
        } catch (e: Exception) {
            val offline = withContext(Dispatchers.IO) {
                app.downloads.readChapterHtml(sourceId, novelPath, chapterPath)
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
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            "Back",
                            tint = pal.text,
                        )
                    }
                },
                actions = {
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

            // progress bar under app bar
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
                    contentPadding = PaddingValues(
                        start = 22.dp,
                        end = 22.dp,
                        top = 20.dp,
                        bottom = 48.dp,
                    ),
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
                    items(paragraphs.size) { i ->
                        Text(
                            text = paragraphs[i],
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
                        
                        val cachedDetail = com.novelreader.data.NovelCache.getDetail(histKey)
                        val chapterIndex = cachedDetail?.chapters?.indexOfFirst { it.path == chapterPath } ?: -1
                        val prevChapter = if (chapterIndex > 0) cachedDetail?.chapters?.get(chapterIndex - 1) else null
                        val nextChapter = if (chapterIndex >= 0 && chapterIndex + 1 < (cachedDetail?.chapters?.size ?: 0)) cachedDetail?.chapters?.get(chapterIndex + 1) else null

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
                                border = BorderStroke(
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
                                border = BorderStroke(
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
                                    "Justify (Kiri-Kanan)",
                                    color = if (alignJustify) pal.accent else pal.muted,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                            TextButton(onClick = { alignJustify = false }) {
                                Text(
                                    "Left (Rata Kiri)",
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

                    // preview
                    Surface(
                        color = pal.bg,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "Pratinjau: ${fontType.label}, ${if (alignJustify) "Rata Kiri-Kanan" else "Rata Kiri"}, nyaman dibaca.",
                            Modifier.padding(14.dp),
                            color = pal.text,
                            fontSize = fontSp.sp,
                            lineHeight = (fontSp * lineMul).sp,
                            fontFamily = fontType.fontFamily,
                            textAlign = if (alignJustify) TextAlign.Justify else TextAlign.Left,
                        )
                    }
                }
            }
        }
    }
}
