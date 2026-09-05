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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.novelreader.core.AppContainer
import com.novelreader.core.AppVmFactory
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

/** Kotatsu Favourites: category tabs + novel grid. */
@Composable
fun FavouritesScreen(
    container: AppContainer,
    onOpenNovel: (String, String) -> Unit,
    vm: FavouritesViewModel = viewModel(factory = AppVmFactory(container)),
) {
    val categories by vm.categories.collectAsState(initial = emptyList())
    val selected by vm.selectedCategory.collectAsState()
    val novels by vm.novels.collectAsState(initial = emptyList())

    Column(Modifier.fillMaxSize()) {
        if (categories.isNotEmpty()) {
            TabRow(selectedTabIndex = categories.indexOfFirst { it.categoryId == selected }
                .coerceAtLeast(0)) {
                categories.forEach { cat ->
                    Tab(
                        selected = cat.categoryId == selected,
                        onClick = { vm.selectCategory(cat.categoryId) },
                        text = { Text(cat.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }
        }
        if (novels.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Default.Favorite,
                    title = "Favourite kosong",
                    hint = "Tambahkan novel lewat ikon favourite di halaman detail.",
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                gridItems(novels, key = { it.novelId }) { f ->
                    NovelGridCard(
                        title = f.title,
                        coverUrl = f.coverUrl,
                        onClick = { onOpenNovel(f.sourceId, f.path) },
                    )
                }
            }
        }
    }
}

/** Kotatsu Feed: favourites with new chapters detected by TrackWorker. */
@Composable
fun FeedScreen(
    container: AppContainer,
    onOpenNovel: (String, String) -> Unit,
    onCheckNow: () -> Unit,
    vm: FeedViewModel = viewModel(factory = AppVmFactory(container)),
) {
    val items by vm.feed.collectAsState(initial = emptyList())
    val local by vm.localEpubs.collectAsState(initial = emptyList())
    LazyColumn(Modifier.fillMaxSize()) {
        if (local.isNotEmpty()) {
            items(count = 1, key = { "local_header" }) {
                Text(
                    "Koleksi Lokal",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                )
            }
            items(count = 1, key = { "local_grid" }) {
                val rows = (local.size + 2) / 3
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .height((rows * 200 + (rows - 1) * 12).dp),
                    contentPadding = PaddingValues(0.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    userScrollEnabled = false,
                ) {
                    gridItems(local, key = { it.epubId }) { e ->
                        NovelGridCard(
                            title = e.title,
                            coverUrl = e.coverUrl,
                            onClick = { onOpenNovel("local_epub", e.epubId) },
                        )
                    }
                }
            }
        }
        items(count = 1, key = { "feed_header" }) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Update terbaru", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onCheckNow) {
                    Icon(Icons.Default.Refresh, "Periksa sekarang")
                }
            }
        }
        if (items.isEmpty()) {
            items(count = 1, key = { "feed_empty" }) {
                Box(
                    Modifier.fillMaxWidth().padding(top = 80.dp, bottom = 80.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyState(
                        icon = Icons.Default.Notifications,
                        title = "Tidak ada chapter baru",
                        hint = "Favourite novel untuk mulai dilacak.",
                    )
                }
            }
        } else {
            items(items, key = { it.novelId }) { t ->
                val sep = t.novelId.indexOf('|')
                val sourceId = if (sep > 0) t.novelId.substring(0, sep) else ""
                val novelPath = if (sep > 0) t.novelId.substring(sep + 1) else ""
                FeedRow(
                    item = t,
                    onClick = {
                        if (sourceId.isNotEmpty()) onOpenNovel(sourceId, novelPath)
                    },
                )
            }
        }
    }
}
