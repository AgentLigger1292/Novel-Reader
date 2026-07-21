package com.novelreader.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.novelreader.NovelApp
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private fun enc(s: String) = URLEncoder.encode(s, StandardCharsets.UTF_8.toString())
private fun dec(s: String) = URLDecoder.decode(s, StandardCharsets.UTF_8.toString())

@Composable
fun AppNav(app: NovelApp) {
    val nav = rememberNavController()
    val back by nav.currentBackStackEntryAsState()
    val route = back?.destination?.route.orEmpty()
    val showBar = route in listOf("browse", "library", "downloads", "history")

    Scaffold(
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    NavigationBarItem(
                        selected = route == "browse",
                        onClick = { nav.navigate("browse") { launchSingleTop = true } },
                        icon = { Icon(Icons.Default.Home, null) },
                        label = { Text("Browse") },
                    )
                    NavigationBarItem(
                        selected = route == "library",
                        onClick = { nav.navigate("library") { launchSingleTop = true } },
                        icon = { Icon(Icons.AutoMirrored.Filled.LibraryBooks, null) },
                        label = { Text("Library") },
                    )
                    NavigationBarItem(
                        selected = route == "downloads",
                        onClick = { nav.navigate("downloads") { launchSingleTop = true } },
                        icon = { Icon(Icons.Default.Download, null) },
                        label = { Text("Offline") },
                    )
                    NavigationBarItem(
                        selected = route == "history",
                        onClick = { nav.navigate("history") { launchSingleTop = true } },
                        icon = { Icon(Icons.Default.History, null) },
                        label = { Text("History") },
                    )
                }
            }
        },
    ) { pad ->
        NavHost(nav, startDestination = "browse", Modifier.padding(pad)) {
            composable("browse") {
                BrowseScreen(
                    app = app,
                    onOpenNovel = { sourceId, path ->
                        nav.navigate("novel/${enc(sourceId)}/${enc(path)}")
                    },
                    onOpenCf = { siteUrl ->
                        nav.navigate("cf/${enc(siteUrl)}")
                    },
                )
            }
            composable("library") {
                LibraryScreen(
                    app = app,
                    onOpenNovel = { sourceId, path ->
                        nav.navigate("novel/${enc(sourceId)}/${enc(path)}")
                    },
                )
            }
            composable("downloads") {
                DownloadsScreen(
                    app = app,
                    onOpenNovel = { sourceId, path ->
                        nav.navigate("novel/${enc(sourceId)}/${enc(path)}")
                    },
                )
            }
            composable("history") {
                HistoryScreen(
                    app = app,
                    onOpenChapter = { sourceId, novelPath, chapterPath, novelTitle ->
                        nav.navigate(
                            "reader/${enc(sourceId)}/${enc(novelPath)}/${enc(chapterPath)}/${enc(novelTitle)}",
                        )
                    },
                )
            }
            composable(
                "cf/{siteUrl}",
                arguments = listOf(navArgument("siteUrl") { type = NavType.StringType }),
            ) { entry ->
                val siteUrl = dec(entry.arguments!!.getString("siteUrl")!!)
                CfWebViewScreen(
                    siteUrl = siteUrl,
                    onDone = {
                        app.onCfCleared()
                        nav.popBackStack()
                    },
                    onBack = { nav.popBackStack() },
                )
            }
            composable(
                "novel/{sourceId}/{path}",
                arguments = listOf(
                    navArgument("sourceId") { type = NavType.StringType },
                    navArgument("path") { type = NavType.StringType },
                ),
            ) { entry ->
                val sourceId = dec(entry.arguments!!.getString("sourceId")!!)
                val path = dec(entry.arguments!!.getString("path")!!)
                NovelDetailScreen(
                    app = app,
                    sourceId = sourceId,
                    path = path,
                    preferOffline = false,
                    onBack = { nav.popBackStack() },
                    onOpenChapter = { chapterPath, novelTitle ->
                        nav.navigate(
                            "reader/${enc(sourceId)}/${enc(path)}/${enc(chapterPath)}/${enc(novelTitle)}",
                        )
                    },
                    onOpenCf = { siteUrl ->
                        nav.navigate("cf/${enc(siteUrl)}")
                    },
                )
            }
            composable(
                "reader/{sourceId}/{novelPath}/{chapterPath}/{novelTitle}",
                arguments = listOf(
                    navArgument("sourceId") { type = NavType.StringType },
                    navArgument("novelPath") { type = NavType.StringType },
                    navArgument("chapterPath") { type = NavType.StringType },
                    navArgument("novelTitle") { type = NavType.StringType },
                ),
            ) { entry ->
                ReaderScreen(
                    app = app,
                    sourceId = dec(entry.arguments!!.getString("sourceId")!!),
                    novelPath = dec(entry.arguments!!.getString("novelPath")!!),
                    chapterPath = dec(entry.arguments!!.getString("chapterPath")!!),
                    novelTitle = dec(entry.arguments!!.getString("novelTitle")!!),
                    onBack = { nav.popBackStack() },
                    onOpenCf = { siteUrl ->
                        nav.navigate("cf/${enc(siteUrl)}")
                    },
                )
            }
        }
    }
}
