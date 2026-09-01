package com.novelreader.ui.explore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.novelreader.core.AppVmFactory
import com.novelreader.core.AppContainer
import com.novelreader.ui.NovelGridCard

/**
 * Kotatsu Explore screen: source picker + paged popular/search grid with
 * infinite scroll load-more.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    container: AppContainer,
    onOpenNovel: (String, String) -> Unit,
    onOpenCf: (String) -> Unit,
    vm: ExploreViewModel = viewModel(factory = AppVmFactory(container)),
) {
    val state by vm.state.collectAsState()
    var sourceMenu by remember { mutableStateOf(false) }
    var coverTick by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val gridState = rememberLazyGridState()
    val source = container.source(state.sourceId)

    // infinite scroll: when near the bottom, ask for the next page
    val nearEnd by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= info.totalItemsCount - 6
        }
    }
    LaunchedEffect(nearEnd, state.novels.size) {
        if (nearEnd && state.novels.isNotEmpty()) vm.loadMore()
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
            androidx.compose.material3.DropdownMenu(
                expanded = sourceMenu,
                onDismissRequest = { sourceMenu = false },
            ) {
                container.sourcesRepository.all.forEach { s ->
                    DropdownMenuItem(
                        text = { Text(s.name) },
                        onClick = {
                            vm.selectSource(s.id)
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
            IconButton(onClick = { vm.reload() }) {
                androidx.compose.material3.Icon(Icons.Default.Refresh, "Reload")
            }
        }
        OutlinedTextField(
            value = state.query,
            onValueChange = { vm.onQueryChanged(it) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Cari novel") },
            placeholder = { Text("min. 2 huruf…") },
        )
        state.error?.let { err ->
            Text(err, color = MaterialTheme.colorScheme.error)
            if (source.siteUrl != null) {
                Button(onClick = { onOpenCf(source.siteUrl!!) }) {
                    Text("Open site (manual CF)")
                }
            }
        }
        Box(Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.novels, key = { it.path }) { n ->
                    NovelGridCard(
                        title = n.title,
                        coverUrl = n.coverUrl,
                        refreshKey = coverTick,
                        onClick = { onOpenNovel(n.sourceId, n.path) },
                    )
                }
                if (state.loading) {
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                        Box(
                            Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(strokeWidth = 2.dp)
                        }
                    }
                }
            }
            if (state.novels.isEmpty() && !state.loading && state.error == null) {
                Text(
                    "Tidak ada novel. CF dulu (shield), atau refresh.",
                    Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
