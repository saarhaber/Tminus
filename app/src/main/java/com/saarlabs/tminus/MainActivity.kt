package com.saarlabs.tminus

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Accessible
import androidx.compose.material.icons.filled.DirectionsTransit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.saarlabs.tminus.ui.TminusEdgeToEdge
import com.saarlabs.tminus.ui.theme.TminusTheme
import com.saarlabs.tminus.ui.theme.rememberUserDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.saarlabs.tminus.ui.AccessibilityEditorRoute
import com.saarlabs.tminus.ui.AccessibilityListScreen
import com.saarlabs.tminus.ui.CommuteEditorRoute
import com.saarlabs.tminus.ui.CommuteListScreen
import com.saarlabs.tminus.ui.LastTrainEditorRoute
import com.saarlabs.tminus.ui.LastTrainListScreen
import com.saarlabs.tminus.ui.FavouriteBoardCard
import com.saarlabs.tminus.ui.HomeBoardsLoading
import com.saarlabs.tminus.ui.HomeBoardsState
import com.saarlabs.tminus.ui.NotificationPermissionGate
import com.saarlabs.tminus.ui.rememberHomeBoards
import com.saarlabs.tminus.ui.rememberUse24HourTime
import com.saarlabs.tminus.ui.SettingsContent
import com.saarlabs.tminus.android.widget.WidgetUpdateWorker

public class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = AppGraph.from(this).settings
        setContent {
            val darkTheme = rememberUserDarkTheme()
            TminusEdgeToEdge(darkTheme)
            TminusTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    NotificationPermissionGate()
                    val rootNav = rememberNavController()
                    NavHost(
                        navController = rootNav,
                        startDestination = ROUTE_MAIN_TABS,
                    ) {
                        composable(ROUTE_MAIN_TABS) {
                            var settingsV3 by remember { mutableStateOf(settings.apiKey().orEmpty()) }
                            var settingsUse24Hour by remember {
                                mutableStateOf(settings.use24HourTime())
                            }
                            TminusApp(
                                rootNavController = rootNav,
                                initialV3 = settingsV3,
                                initialUse24Hour = settingsUse24Hour,
                                onSaveSettings = { v3, use24Hour ->
                                    settings.setApiKey(v3)
                                    settings.setUse24HourTime(use24Hour)
                                    settingsV3 = settings.apiKey().orEmpty()
                                    settingsUse24Hour = settings.use24HourTime()
                                    runCatching {
                                        AppGraph.from(this@MainActivity).onApiKeyChanged()
                                        WidgetUpdateWorker.enqueueRefresh(this@MainActivity)
                                    }.onFailure { Log.e(TAG, "applying settings failed", it) }
                                },
                            )
                        }
                        composable(ROUTE_COMMUTE_LIST) {
                            CommuteListScreen(navController = rootNav)
                        }
                        composable("$ROUTE_COMMUTE_EDIT/{profileId}") { entry ->
                            val id = entry.arguments?.getString("profileId") ?: "new"
                            CommuteEditorRoute(navController = rootNav, profileId = id)
                        }
                        composable(ROUTE_LAST_TRAIN_LIST) {
                            LastTrainListScreen(navController = rootNav)
                        }
                        composable("$ROUTE_LAST_TRAIN_EDIT/{profileId}") { entry ->
                            val id = entry.arguments?.getString("profileId") ?: "new"
                            LastTrainEditorRoute(navController = rootNav, profileId = id)
                        }
                        composable(ROUTE_ACCESS_LIST) {
                            AccessibilityListScreen(navController = rootNav)
                        }
                        composable("$ROUTE_ACCESS_EDIT/{id}") { entry ->
                            val id = entry.arguments?.getString("id") ?: "new"
                            AccessibilityEditorRoute(navController = rootNav, id = id)
                        }
                    }
                }
            }
        }
    }

    public companion object {
        private const val TAG = "MainActivity"

        public const val ROUTE_MAIN_TABS: String = "main_tabs"
        public const val ROUTE_COMMUTE_LIST: String = "commute_list"
        public const val ROUTE_COMMUTE_EDIT: String = "commute_edit"
        public const val ROUTE_LAST_TRAIN_LIST: String = "last_train_list"
        public const val ROUTE_LAST_TRAIN_EDIT: String = "last_train_edit"
        public const val ROUTE_ACCESS_LIST: String = "access_list"
        public const val ROUTE_ACCESS_EDIT: String = "access_edit"
    }
}

private sealed class MainDestination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
) {
    data object Home : MainDestination("home", R.string.nav_home, Icons.Default.Home)

    data object Settings : MainDestination("settings", R.string.nav_settings, Icons.Default.Settings)

    companion object {
        val entries = listOf(Home, Settings)
    }
}

@Composable
private fun TminusApp(
    rootNavController: NavHostController,
    initialV3: String,
    initialUse24Hour: Boolean,
    onSaveSettings: (String, Boolean) -> Unit,
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val current = navBackStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                MainDestination.entries.forEach { dest ->
                    val selected = current?.hierarchy?.any { it.route == dest.route } == true
                    val tabLabel = stringResource(dest.labelRes)
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(dest.icon, contentDescription = tabLabel) },
                        label = { Text(tabLabel) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = MainDestination.Home.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(MainDestination.Home.route) {
                HomeTab(
                    onOpenCommutes = {
                        rootNavController.navigate(MainActivity.ROUTE_COMMUTE_LIST)
                    },
                    onOpenLastTrain = {
                        rootNavController.navigate(MainActivity.ROUTE_LAST_TRAIN_LIST)
                    },
                    onOpenAccessibility = {
                        rootNavController.navigate(MainActivity.ROUTE_ACCESS_LIST)
                    },
                )
            }
            composable(MainDestination.Settings.route) {
                SettingsContent(
                    initialV3 = initialV3,
                    initialUse24Hour = initialUse24Hour,
                    onSave = onSaveSettings,
                )
            }
        }
    }
}

@Composable
private fun HomeTab(
    onOpenCommutes: () -> Unit,
    onOpenLastTrain: () -> Unit,
    onOpenAccessibility: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        HomeHeader()
        Spacer(Modifier.height(20.dp))

        HomeDeparturesSection(onOpenCommutes = onOpenCommutes)

        FeatureCard(
            title = stringResource(R.string.home_commutes_button),
            subtitle = stringResource(R.string.home_feature_commutes_desc),
            icon = Icons.Filled.DirectionsTransit,
            background = scheme.primaryContainer,
            iconBackdrop = scheme.primary,
            iconTint = scheme.onPrimary,
            titleColor = scheme.onPrimaryContainer,
            subtitleColor = scheme.onPrimaryContainer.copy(alpha = 0.82f),
            onClick = onOpenCommutes,
        )
        Spacer(Modifier.height(12.dp))
        FeatureCard(
            title = stringResource(R.string.home_last_train_button),
            subtitle = stringResource(R.string.home_feature_last_train_desc),
            icon = Icons.Filled.NightsStay,
            background = scheme.tertiaryContainer,
            iconBackdrop = scheme.tertiary,
            iconTint = scheme.onTertiary,
            titleColor = scheme.onTertiaryContainer,
            subtitleColor = scheme.onTertiaryContainer.copy(alpha = 0.82f),
            onClick = onOpenLastTrain,
        )
        Spacer(Modifier.height(12.dp))
        FeatureCard(
            title = stringResource(R.string.home_access_button),
            subtitle = stringResource(R.string.home_feature_access_desc),
            icon = Icons.Filled.Accessible,
            background = scheme.secondaryContainer,
            iconBackdrop = scheme.secondary,
            iconTint = scheme.onSecondary,
            titleColor = scheme.onSecondaryContainer,
            subtitleColor = scheme.onSecondaryContainer.copy(alpha = 0.82f),
            onClick = onOpenAccessibility,
        )

        Spacer(Modifier.height(24.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.home_tip_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = scheme.primary,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.home_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/**
 * Live departures from the user's starred stations, above the navigation cards.
 *
 * Home is the first screen of a transit app; it should answer "when is my next train" before it
 * offers settings. Stations are starred from any stop picker.
 */
@Composable
private fun HomeDeparturesSection(onOpenCommutes: () -> Unit) {
    val use24Hour = rememberUse24HourTime()
    when (val state = rememberHomeBoards()) {
        HomeBoardsState.Loading -> {
            SectionHeading(stringResource(R.string.home_board_section))
            HomeBoardsLoading()
            Spacer(Modifier.height(12.dp))
        }

        HomeBoardsState.NoFavourites -> Unit

        is HomeBoardsState.Ready -> {
            if (state.boards.isEmpty()) return
            SectionHeading(stringResource(R.string.home_board_section))
            state.boards.forEach { board ->
                FavouriteBoardCard(
                    board = board,
                    use24Hour = use24Hour,
                    onClick = onOpenCommutes,
                )
                Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun HomeHeader() {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Box(
            modifier =
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(listOf(scheme.primary, scheme.tertiary)),
                    ),
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.DirectionsTransit,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = stringResource(R.string.home_title),
                style = MaterialTheme.typography.headlineMedium,
                color = scheme.onSurface,
            )
            Text(
                text = stringResource(R.string.home_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FeatureCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    background: Color,
    iconBackdrop: Color,
    iconTint: Color,
    titleColor: Color,
    subtitleColor: Color,
    onClick: () -> Unit,
) {
    // Merged into one semantics node with a Button role: TalkBack previously read the title,
    // subtitle and arrow as three unrelated nodes with no indication the card was tappable.
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .clickable(onClick = onClick)
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    contentDescription = "$title. $subtitle"
                },
        colors = CardDefaults.cardColors(containerColor = background),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(PaddingValues(horizontal = 18.dp, vertical = 18.dp)),
        ) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Box(
                    modifier =
                        Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(iconBackdrop),
                    contentAlignment = androidx.compose.ui.Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = titleColor,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = subtitleColor,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = titleColor,
                )
            }
        }
    }
}
