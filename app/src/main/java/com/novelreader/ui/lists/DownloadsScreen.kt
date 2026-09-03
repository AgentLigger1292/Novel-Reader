package com.novelreader.ui.lists

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.novelreader.core.AppContainer
import com.novelreader.data.DownloadEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private val deleteScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/** Downloads list — folder format unchanged; delete still works offline. */
@Composable
fun DownloadsScreen(
    container: AppContainer,
    onOpenNovel: (String, String) -> Unit,
) {
    val items by container.downloads.entries.collectAsState()
    var pendingDelete by remember { mutableStateOf<DownloadEntry?>(null) }

    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                icon = Icons.Default.Download,
                title = "Belum ada unduhan",
                hint = "Buka novel → ikon Download untuk simpan offline.",
            )
        }
        return
    }

    LazyColumn(Modifier.fillMaxSize()) {
        items(items, key = { it.key }) { e ->
            DownloadRow(
                entry = e,
                onDelete = { pendingDelete = e },
                onClick = { onOpenNovel(e.sourceId, e.novelPath) },
            )
        }
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Hapus unduhan?") },
            text = { Text("File offline \"${target.title}\" akan dihapus dari perangkat.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteScope.launch {
                            container.downloads.delete(target.sourceId, target.novelPath)
                        }
                        pendingDelete = null
                    },
                ) {
                    Text("Hapus", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Batal") }
            },
        )
    }
}
