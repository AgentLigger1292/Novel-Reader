package com.novelreader.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
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
import com.novelreader.ui.details.DetailsScreen
import com.novelreader.ui.explore.ExploreScreen
import com.novelreader.ui.lists.DownloadsScreen
import com.novelreader.ui.lists.FeedScreen
import com.novelreader.ui.lists.FavouritesScreen
import com.novelreader.ui.lists.HistoryScreen
import com.novelreader.ui.reader.ReaderScreen
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private fun enc(s: String) = URLEncoder.encode(s, StandardCharsets.UTF_8.toString())
private fun dec(s: String) = URLDecoder.decode(s, StandardCharsets.UTF_8.toString())

/** Kotatsu NavItem equivalent: FEED, HISTORY, FAVOURITES, EXPLORE. */
private data class NavItem(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

private val NAV_ITEMS = listOf(
    NavItem("feed", "Feed", Icons.AutoMirrored.Filled.MenuBook),
    NavItem("history", "History", Icons.Default.History),
    NavItem("favourites", "Favourite", Icons.Default.Favorite),
    NavItem("explore", "Explore", Icons.Default.Explore),
)

@Composable
fun AppNav(app: NovelApp) {
    val container = app.container
    val nav = rememberNavController()
    val back by nav.currentBackStackEntryAsState()
    val route = back?.destination?.route.orEmpty()

    Scaffold(
        bottomBar = {
            if (route in NAV_ITEMS.map { it.route }) {
                NavigationBar {
                    NAV_ITEMS.forEach { item ->
                        NavigationBarItem(
                            selected = route == item.route,
                            onClick = {
                                nav.navigate(item.route) {
                                    popUpTo("feed") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, null) },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
        },
    ) { pad ->
        NavHost(nav, startDestination = "feed", Modifier.padding(pad)) {
            composable("feed") {
                FeedScreen(
                    container = container,
                    onOpenNovel = { sourceId, path ->
                        nav.navigate("novel/${enc(sourceId)}/${enc(path)}")
                    },
                    onCheckNow = { app.runTrackerNow() },
                )
            }
            composable("history") {
                HistoryScreen(
                    container = container,
                    onOpenChapter = { sourceId, novelPath, chapterPath, novelTitle ->
                        nav.navigate(
                            "reader/${enc(sourceId)}/${enc(novelPath)}/${enc(chapterPath)}/${enc(novelTitle)}",
                        )
                    },
                )
            }
            composable("favourites") {
                FavouritesScreen(
                    container = container,
                    onOpenNovel = { sourceId, path ->
                        nav.navigate("novel/${enc(sourceId)}/${enc(path)}")
                    },
                )
            }
            composable("explore") {
                ExploreScreen(
                    container = container,
                    onOpenNovel = { sourceId, path ->
                        nav.navigate("novel/${enc(sourceId)}/${enc(path)}")
                    },
                    onOpenCf = { siteUrl -> nav.navigate("cf/${enc(siteUrl)}") },
                )
            }
            composable("downloads") {
                DownloadsScreen(
                    container = container,
                    onOpenNovel = { sourceId, path ->
                        nav.navigate("novel/${enc(sourceId)}/${enc(path)}")
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
                    onDone = { nav.popBackStack() },
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
                DetailsScreen(
                    container = container,
                    sourceId = sourceId,
                    path = path,
                    onBack = { nav.popBackStack() },
                    onOpenChapter = { chapterPath, novelTitle ->
                        nav.navigate(
                            "reader/${enc(sourceId)}/${enc(path)}/${enc(chapterPath)}/${enc(novelTitle)}",
                        )
                    },
                    onOpenCf = { siteUrl -> nav.navigate("cf/${enc(siteUrl)}") },
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
                val srcId = dec(entry.arguments!!.getString("sourceId")!!)
                val novPath = dec(entry.arguments!!.getString("novelPath")!!)
                ReaderScreen(
                    container = container,
                    sourceId = srcId,
                    novelPath = novPath,
                    chapterPath = dec(entry.arguments!!.getString("chapterPath")!!),
                    novelTitle = dec(entry.arguments!!.getString("novelTitle")!!),
                    onBack = { nav.popBackStack() },
                    onOpenChapter = { targetChapterPath, title ->
                        nav.navigate(
                            "reader/${enc(srcId)}/${enc(novPath)}/${enc(targetChapterPath)}/${enc(title)}",
                        ) {
                            popUpTo("reader/{sourceId}/{novelPath}/{chapterPath}/{novelTitle}") {
                                inclusive = true
                            }
                        }
                    },
                    onOpenCf = { siteUrl -> nav.navigate("cf/${enc(siteUrl)}") },
                )
            }
        }
    }
}
