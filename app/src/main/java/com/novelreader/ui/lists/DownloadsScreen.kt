package com.novelreader.ui.lists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val deleteScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/** Downloads list — folder format unchanged; delete still works offline. */
@Composable
fun DownloadsScreen(
    container: com.novelreader.core.AppContainer,
    onOpenNovel: (String, String) -> Unit,
) {
    val items by container.downloads.entries.collectAsState()
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
                        deleteScope.launch {
                            container.downloads.delete(e.sourceId, e.novelPath)
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
