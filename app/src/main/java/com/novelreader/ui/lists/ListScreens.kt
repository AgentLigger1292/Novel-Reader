package com.novelreader.ui.lists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import android.content.Intent
import android.net.Uri
import com.novelreader.core.AppContainer
import com.novelreader.core.AppVmFactory
import com.novelreader.core.update.AppUpdate
import com.novelreader.ui.NovelGridCard

/** Kotatsu History screen: full list with cover, resume + progress percent. */
@Composable
fun HistoryScreen(
    container: AppContainer,
    onOpenChapter: (sourceId: String, novelPath: String, chapterPath: String, novelTitle: String) -> Unit,
    vm: HistoryViewModel = viewModel(factory = AppVmFactory(container)),
) {
    val items by vm.history.collectAsState(initial = emptyList())
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                icon = Icons.Default.History,
                title = "Belum ada riwayat baca",
                hint = "Novel yang kamu baca akan muncul di sini.",
            )
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(items, key = { it.novelId }) { e ->
            val sep = e.novelId.indexOf('|')
            val sourceId = if (sep > 0) e.novelId.substring(0, sep) else ""
            val novelPath = if (sep > 0) e.novelId.substring(sep + 1) else ""
            HistoryRow(
                item = e,
                onClick = {
                    if (sourceId.isNotEmpty()) {
                        onOpenChapter(sourceId, novelPath, e.chapterId, e.chapterName)
                    }
                },
            )
        }
    }
}

/**
 * Library: the user's collection — favourites plus imported local EPUBs.
 * A GitHub app-update banner may appear at the top when a newer release exists.
 */
@Composable
fun FeedScreen(
    container: AppContainer,
    onOpenNovel: (String, String) -> Unit,
    vm: FeedViewModel = viewModel(factory = AppVmFactory(container)),
) {
    val favourites by vm.favourites.collectAsState(initial = emptyList())
    val local by vm.localEpubs.collectAsState(initial = emptyList())
    val update by vm.appUpdate.collectAsState()
    val context = LocalContext.current
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 130.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (update != null) {
            item(key = "update_banner", span = { GridItemSpan(maxLineSpan) }) {
                UpdateBanner(
                    update = update!!,
                    onUpdate = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(update!!.releaseUrl)),
                            )
                        }
                    },
                    onDismiss = { vm.dismissUpdate(update!!.versionName) },
                )
            }
        }
        if (local.isNotEmpty()) {
            item(key = "local_header", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "Koleksi Lokal",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                )
            }
            gridItems(local, key = { "local_${it.epubId}" }) { e ->
                NovelGridCard(
                    title = e.title,
                    coverUrl = e.coverUrl,
                    onClick = { onOpenNovel("local_epub", e.epubId) },
                )
            }
        }
        item(key = "fav_header", span = { GridItemSpan(maxLineSpan) }) {
            Text(
                "Koleksi",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = if (local.isNotEmpty()) 12.dp else 0.dp, bottom = 4.dp),
            )
        }
        if (favourites.isEmpty()) {
            item(key = "fav_empty", span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    Modifier.fillMaxWidth().padding(top = 80.dp, bottom = 80.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyState(
                        icon = Icons.Default.Favorite,
                        title = "Koleksi kosong",
                        hint = "Tambahkan novel lewat ikon favourite di halaman detail.",
                    )
                }
            }
        } else {
            gridItems(favourites, key = { it.novelId }) { f ->
                NovelGridCard(
                    title = f.title,
                    coverUrl = f.coverUrl,
                    onClick = { onOpenNovel(f.sourceId, f.path) },
                )
            }
        }
    }
}

/** Banner shown at the top of the Library when a newer app version is published on GitHub. */
@Composable
private fun UpdateBanner(
    update: AppUpdate,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
) {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 4.dp),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                "Pembaruan tersedia: v${update.versionName}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            if (!update.notes.isNullOrBlank()) {
                Text(
                    update.notes.lines().first().take(120),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(onClick = onDismiss) { Text("Nanti") }
                Button(onClick = onUpdate) { Text("Perbarui") }
            }
        }
    }
}
