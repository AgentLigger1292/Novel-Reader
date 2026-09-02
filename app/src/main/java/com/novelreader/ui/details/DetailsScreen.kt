package com.novelreader.ui.details

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.WorkInfo
import com.novelreader.core.AppContainer
import com.novelreader.core.AppVmFactory
import com.novelreader.work.DownloadWorker

/**
 * Kotatsu Details screen: meta + cached chapter list with downloaded badges,
 * favourite (Room), and WorkManager-driven downloads that survive navigation.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(
    container: AppContainer,
    sourceId: String,
    path: String,
    onBack: () -> Unit,
    onOpenChapter: (chapterPath: String, novelTitle: String) -> Unit,
    onOpenCf: (String) -> Unit,
    vm: DetailsViewModel = viewModel(factory = AppVmFactory(container)),
) {
    val state by vm.state.collectAsState()
    var newestFirst by remember { mutableStateOf(false) }
    var descExpanded by remember { mutableStateOf(false) }
    val source = container.source(sourceId)

    LaunchedEffect(sourceId, path) {
        vm.load(sourceId, path)
    }

    val novelId = state.novelId
    val isFavourite by (if (novelId.isNotEmpty()) {
        vm.observeFavourite(novelId).collectAsState(initial = false)
    } else {
        remember { mutableStateOf(false) }
    })

    val dlEntry = container.downloads.entries.collectAsState(initial = emptyList()).value
        .find { it.key == novelId }
    // live progress from the worker (DownloadStore.liveProgress set inside downloadAll)
    val liveProgress by container.downloads.liveProgress.collectAsState()
    val live = liveProgress
    val downloading = live != null && live.first == novelId && live.second < live.third
    val dlDone = live?.takeIf { it.first == novelId }?.second ?: 0
    val dlTotal = live?.takeIf { it.first == novelId }?.third ?: 0

    val chapters = remember(state.chapters, newestFirst) {
        if (newestFirst) state.chapters.asReversed() else state.chapters
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    state.detail?.novel?.title ?: "…",
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
                    enabled = !downloading && state.detail != null,
                    onClick = {
                        val request = androidx.work.OneTimeWorkRequestBuilder<DownloadWorker>()
                            .setInputData(
                                androidx.work.Data.Builder()
                                    .putString(DownloadWorker.KEY_SOURCE, sourceId)
                                    .putString(DownloadWorker.KEY_NOVEL_PATH, path)
                                    .putString(
                                        DownloadWorker.KEY_TITLE,
                                        state.detail?.novel?.title ?: "Novel",
                                    )
                                    .build(),
                            )
                            .addTag(novelId)
                            .build()
                        container.workManager().enqueueUniqueWork(
                            DownloadWorker.uniqueName(novelId),
                            androidx.work.ExistingWorkPolicy.KEEP,
                            request,
                        )
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
                IconButton(onClick = { vm.toggleFavourite(isFavourite) }) {
                    Icon(
                        if (isFavourite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        "Favourite",
                    )
                }
            },
        )

        when {
            state.loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                }
            }
            state.error != null -> {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(state.error!!)
                    if (source.siteUrl != null) {
                        Button(onClick = { onOpenCf(source.siteUrl!!) }) {
                            Text("Open site (manual CF)")
                        }
                    }
                }
            }
            else -> {
                val d = state.detail
                Column(Modifier.fillMaxSize()) {
                    if (downloading) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(
                                "Mengunduh $dlDone/$dlTotal",
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
                    if (d != null) {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
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
                                            onOpenChapter(first.chapterId, d.novel.title)
                                        },
                                    ) { Text("Mulai Ch.1") }
                                    OutlinedButton(
                                        onClick = {
                                            val last = if (newestFirst) chapters.first() else chapters.last()
                                            onOpenChapter(last.chapterId, d.novel.title)
                                        },
                                    ) { Text("Terbaru") }
                                }
                            }
                        }
                    }

                    HorizontalDivider()
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "${chapters.size} chapter",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                        androidx.compose.material3.TextButton(onClick = { newestFirst = !newestFirst }) {
                            Icon(
                                if (newestFirst) Icons.Default.ArrowDownward
                                else Icons.Default.ArrowUpward,
                                contentDescription = null,
                            )
                            Text(if (newestFirst) " Baru→Lama" else " Lama→Baru")
                        }
                    }
                    HorizontalDivider()

                    LazyColumn(Modifier.fillMaxSize()) {
                        items(chapters, key = { it.chapterId }) { ch ->
                            val downloaded = container.downloads.hasChapter(
                                sourceId, path, ch.chapterId,
                            )
                            val numLabel = ch.number?.let { n ->
                                if (n == n.toLong().toFloat()) n.toLong().toString() else n.toString()
                            }
                            ListItem(
                                headlineContent = {
                                    Text(
                                        ch.name + if (downloaded) "  ✓" else "",
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
                                    onOpenChapter(ch.chapterId, d?.novel?.title ?: "Chapter")
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
