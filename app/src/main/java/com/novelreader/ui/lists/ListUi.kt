package com.novelreader.ui.lists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.novelreader.core.db.HistoryWithNovel
import com.novelreader.core.db.TrackWithNovel
import com.novelreader.data.DownloadEntry
import com.novelreader.network.CoverLoader
import java.text.DateFormat
import java.util.Date

/** Shared empty state: big tinted icon + title + hint, centered. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    hint: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            modifier = Modifier.size(48.dp),
        )
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
        if (hint != null) {
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * Small rounded cover thumbnail for list rows. Book placeholder keeps a
 * consistent 44x61dp footprint whether the cover exists or not.
 */
@Composable
private fun NovelCoverThumb(
    coverUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val imageLoader = remember { CoverLoader.get(context) }
    val model = remember(coverUrl) { CoverLoader.request(context, coverUrl) }

    Box(
        modifier
            .aspectRatio(0.72f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (model != null) {
            SubcomposeAsyncImage(
                model = model,
                imageLoader = imageLoader,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = {},
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
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/** History list row: cover + novel title + chapter + progress + last-read date. */
@Composable
fun HistoryRow(
    item: HistoryWithNovel,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(item.novelTitle ?: item.chapterName, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Column {
                if (item.novelTitle != null) {
                    Text(
                        item.chapterName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (item.percent > 0f) {
                    LinearProgressIndicator(
                        progress = { item.percent.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    )
                }
                Text(
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                        .format(Date(item.updatedAt)),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        },
        leadingContent = {
            NovelCoverThumb(
                coverUrl = item.novelCoverUrl,
                contentDescription = null,
                modifier = Modifier.width(44.dp),
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** Feed list row: cover + title + new-chapter count + badge. */
@Composable
fun FeedRow(
    item: TrackWithNovel,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = { Text("${item.newChapters} chapter baru") },
        leadingContent = {
            NovelCoverThumb(
                coverUrl = item.coverUrl,
                contentDescription = null,
                modifier = Modifier.width(44.dp),
            )
        },
        trailingContent = { Badge { Text("+${item.newChapters}") } },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** Downloads list row: cover + title + offline chapter count + delete button. */
@Composable
fun DownloadRow(
    entry: DownloadEntry,
    onDelete: () -> Unit,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(entry.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text("${entry.downloadedCount}/${entry.chapterCount} chapter · offline")
        },
        leadingContent = {
            NovelCoverThumb(
                coverUrl = entry.coverUrl,
                contentDescription = null,
                modifier = Modifier.width(44.dp),
            )
        },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Hapus ${entry.title}")
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Preview(name = "EmptyState", showBackground = true)
@Composable
private fun EmptyStatePreview() {
    MaterialTheme {
        EmptyState(
            icon = Icons.AutoMirrored.Filled.MenuBook,
            title = "Favourite masih kosong",
            hint = "Tambahkan novel dari halaman detail novel.",
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview(name = "HistoryRow", showBackground = true)
@Composable
private fun HistoryRowPreview() {
    MaterialTheme {
        HistoryRow(
            item = HistoryWithNovel(
                novelId = "sonicmtl|some-novel",
                chapterId = "chapter-12",
                chapterName = "Chapter 12 — Petualangan Berlanjut",
                scroll = 0.3f,
                percent = 0.45f,
                chaptersCount = 120,
                updatedAt = System.currentTimeMillis(),
                novelTitle = "The Legendary Mechanic",
                novelCoverUrl = null,
            ),
            onClick = {},
        )
    }
}

@Preview(name = "FeedRow", showBackground = true)
@Composable
private fun FeedRowPreview() {
    MaterialTheme {
        FeedRow(
            item = TrackWithNovel(
                novelId = "sonicmtl|some-novel",
                newChapters = 3,
                lastChapterCount = 122,
                lastCheckTime = System.currentTimeMillis(),
                title = "The Legendary Mechanic",
                sourceId = "sonicmtl",
                path = "some-novel",
                coverUrl = null,
                author = null,
            ),
            onClick = {},
        )
    }
}

@Preview(name = "DownloadRow", showBackground = true)
@Composable
private fun DownloadRowPreview() {
    MaterialTheme {
        DownloadRow(
            entry = DownloadEntry(
                key = "sonicmtl|some-novel",
                sourceId = "sonicmtl",
                novelPath = "some-novel",
                title = "The Legendary Mechanic",
                author = null,
                coverUrl = null,
                chapterCount = 120,
                downloadedCount = 87,
            ),
            onDelete = {},
            onClick = {},
        )
    }
}
