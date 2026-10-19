package com.vknxworks.paperctrl.ui.nav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.paperctrl.container
import com.vknxworks.paperctrl.ui.auth.ServerSetupScreen
import com.vknxworks.paperctrl.ui.components.LoadingView
import com.vknxworks.paperctrl.ui.settings.ServersScreen
import com.vknxworks.paperctrl.ui.upload.QueueScreen
import com.vknxworks.paperctrl.notify.Notifications
import com.vknxworks.paperctrl.R
import com.vknxworks.paperctrl.ui.components.LocalBottomClearance
import com.vknxworks.paperctrl.ui.components.LocalHazeState
import com.vknxworks.paperctrl.ui.settings.SettingsScreen
import com.vknxworks.paperctrl.ui.settings.UsageStatsPrompt
import com.vknxworks.paperctrl.ui.settings.YouScreen
import com.vknxworks.paperctrl.ui.docs.DocumentsTab
import com.vknxworks.paperctrl.ui.inbox.InboxTab
import com.vknxworks.paperctrl.ui.organise.OrganiseTab
import com.vknxworks.paperctrl.ui.organise.ObjectListScreen
import com.vknxworks.paperctrl.ui.organise.TrashScreen
import com.vknxworks.paperctrl.ui.tabs.ScanTab
import com.vknxworks.paperctrl.ui.document.DocumentScreen
import com.vknxworks.paperctrl.scan.ScannerScreen
import com.vknxworks.paperctrl.scan.ScanReviewScreen
import com.vknxworks.paperctrl.scan.PageEditScreen
import com.vknxworks.paperctrl.ui.settings.NtfyScreen
import androidx.navigation.toRoute
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

@Composable
fun AppRoot() {
    val c = LocalContext.current.container
    val ready by c.sessions.ready.collectAsStateWithLifecycle()
    val active by c.sessions.active.collectAsStateWithLifecycle()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when {
            !ready -> LoadingView()
            active == null -> ServerSetupScreen(onDone = {}, onCancel = null)
            // A fresh navigation stack per server, so nothing from the previous server stays on screen.
            else -> key(active!!.server.id) { MainScaffold() }
        }
    }
}

private data class TopDest(val route: Any, val label: Int, val icon: ImageVector, val selectedIcon: ImageVector)

private val topDestinations = listOf(
    TopDest(InboxRoute, R.string.tab_inbox, Icons.Outlined.Inbox, Icons.Filled.Inbox),
    TopDest(DocumentsRoute, R.string.tab_documents, Icons.Outlined.Description, Icons.Filled.Description),
    TopDest(ScanRoute, R.string.tab_scan, Icons.Outlined.DocumentScanner, Icons.Filled.DocumentScanner),
    TopDest(OrganiseRoute, R.string.tab_organise, Icons.Outlined.Sell, Icons.Filled.Sell),
)

@Composable
private fun MainScaffold() {
    val navController = rememberNavController()
    val navigator = remember(navController) { Navigator(navController) }
    val backStack by navController.currentBackStackEntryAsState()
    val dest = backStack?.destination
    val selectedIndex = topDestinations.indexOfFirst { top -> dest?.hierarchy?.any { it.hasRoute(top.route::class) } == true }
    val items = topDestinations.map { TabItem(stringResource(it.label), it.icon, it.selectedIcon) }

    fun selectTab(i: Int) {
        navController.navigate(topDestinations[i].route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    val c = LocalContext.current.container

    // Usage stats (only if the user opted in): the kind of screen, never what it shows.
    val entryId = backStack?.id
    val screen = dest?.route?.let(::screenName)
    LaunchedEffect(entryId) {
        if (screen != null) c.telemetry.track("screen_viewed", mapOf("screen" to screen))
    }
    UsageStatsPrompt()

    // A notification, shortcut, widget or tile asked for a screen.
    val pendingOpen by c.pendingOpen.collectAsStateWithLifecycle()
    LaunchedEffect(pendingOpen) {
        val open = pendingOpen ?: return@LaunchedEffect
        c.pendingOpen.value = null
        when {
            open == Notifications.OPEN_QUEUE -> navController.navigate(QueueRoute) { launchSingleTop = true }
            open == Notifications.OPEN_SCAN -> {
                selectTab(2)
                navController.navigate(ScannerRoute()) { launchSingleTop = true }
            }
            open == Notifications.OPEN_INBOX -> selectTab(0)
            open == Notifications.OPEN_SEARCH -> selectTab(1)
            open.startsWith(Notifications.OPEN_DOCUMENT) ->
                open.removePrefix(Notifications.OPEN_DOCUMENT).toIntOrNull()?.let { navController.navigate(DocumentRoute(it)) }
        }
    }

    CompositionLocalProvider(LocalNavigator provides navigator) {
        BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (maxWidth >= 600.dp) {
                // Tablets and unfolded foldables: a side rail instead of the floating bar.
                Row(Modifier.fillMaxSize()) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.background,
                        modifier = Modifier.fillMaxHeight().statusBarsPadding(),
                    ) {
                        Spacer(Modifier.weight(1f))
                        items.forEachIndexed { i, item ->
                            val selected = i == selectedIndex
                            NavigationRailItem(
                                selected = selected,
                                onClick = { selectTab(i) },
                                icon = { Icon(if (selected) item.selectedIcon else item.icon, contentDescription = item.label) },
                                label = { Text(item.label) },
                            )
                        }
                        Spacer(Modifier.weight(1f))
                    }
                    Box(Modifier.weight(1f).fillMaxHeight().navigationBarsPadding()) { AppNavHost(navController) }
                }
            } else {
                PhoneLayout(navController, items, showTabs = selectedIndex >= 0, selectedIndex = selectedIndex, onSelect = ::selectTab)
            }
        }
    }
}

/**
 * Phones: content runs edge to edge and scrolls under a floating glass island.
 * The island shows on the main tabs; pushed screens get the full height.
 */
@Composable
private fun PhoneLayout(
    navController: NavHostController,
    items: List<TabItem>,
    showTabs: Boolean,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    val hazeState = rememberHazeState()
    val density = LocalDensity.current
    var overlayHeight by remember { mutableStateOf(0.dp) }

    CompositionLocalProvider(
        LocalHazeState provides hazeState,
        LocalBottomClearance provides if (showTabs) overlayHeight else 0.dp,
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().hazeSource(hazeState).then(if (showTabs) Modifier else Modifier.navigationBarsPadding())) {
                AppNavHost(navController)
            }
            AnimatedVisibility(
                visible = showTabs,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { overlayHeight = with(density) { it.height.toDp() } }
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp),
            ) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    GlassTabBar(items = items, selectedIndex = selectedIndex, onSelect = onSelect)
                }
            }
        }
    }
}

/** "com.vknxworks.paperctrl.ui.nav.QueueRoute" -> "Queue". Only the route's class name, never its arguments. */
private fun screenName(route: String): String =
    route.substringBefore('/').substringBefore('?').substringAfterLast('.').removeSuffix("Route")

@Composable
private fun AppNavHost(navController: NavHostController) {
    NavHost(navController, startDestination = InboxRoute) {
        composable<InboxRoute> { InboxTab() }
        composable<DocumentsRoute> { DocumentsTab() }
        composable<ScanRoute> { ScanTab() }
        composable<OrganiseRoute> { OrganiseTab() }

        composable<YouRoute> { YouScreen() }
        composable<SettingsRoute> { SettingsScreen() }
        composable<ServersRoute> { ServersScreen() }
        composable<QueueRoute> { QueueScreen() }
        composable<ScannerRoute> { ScannerScreen(it.toRoute<ScannerRoute>().retakeId) }
        composable<ScanReviewRoute> { ScanReviewScreen() }
        composable<PageEditRoute> { PageEditScreen(it.toRoute<PageEditRoute>().pageId) }
        composable<DocumentRoute> { DocumentScreen(it.toRoute<DocumentRoute>().id) }
        composable<ObjectListRoute> { ObjectListScreen(it.toRoute<ObjectListRoute>().kind) }
        composable<TrashRoute> { TrashScreen() }
        composable<NtfyRoute> { NtfyScreen() }
        composable<AddServerRoute> {
            val navigator = LocalNavigator.current
            ServerSetupScreen(onDone = { navController.popBackStack(InboxRoute, false) }, onCancel = navigator::back)
        }
    }
}
