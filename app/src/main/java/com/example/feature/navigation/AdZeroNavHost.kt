package com.example.feature.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.core.common.AdZeroViewModelFactory
import com.example.core.common.AppContainer
import com.example.feature.activity.ActivityAnalyticsScreen
import com.example.feature.activity.ActivityViewModel
import com.example.feature.apps.AppsControlScreen
import com.example.feature.apps.AppsViewModel
import com.example.feature.dns.SmartDnsScreen
import com.example.feature.dns.SmartDnsViewModel
import com.example.feature.ghost.GhostInboxScreen
import com.example.feature.ghost.GhostSchedulerScreen
import com.example.feature.ghost.GhostViewModel
import com.example.feature.home.HomeScreen
import com.example.feature.home.HomeViewModel
import com.example.feature.settings.AboutAdZeroScreen
import com.example.feature.settings.BatteryPerformanceScreen
import com.example.feature.settings.BlocklistsScreen
import com.example.feature.settings.CustomRulesScreen
import com.example.feature.settings.DiagnosticsScreen
import com.example.feature.settings.PrivacyPolicyScreen
import com.example.feature.settings.SettingsScreen
import com.example.feature.settings.SettingsViewModel
import com.example.feature.youtube.YouTubeAssistantScreen
import com.example.ui.theme.AdZeroLavenderSurface
import com.example.ui.theme.AdZeroMutedText
import com.example.ui.theme.AdZeroPurple
import com.example.ui.theme.AdZeroWhite

object AdZeroRoutes {
    const val HOME = "home"
    const val APPS = "apps"
    const val GHOST = "ghost"
    const val ACTIVITY = "activity"
    const val SETTINGS = "settings"

    const val GHOST_SCHEDULER = "ghost_scheduler"
    const val SMART_DNS = "smart_dns"
    const val YOUTUBE_ASSISTANT = "youtube_assistant"
    const val BLOCKLISTS = "blocklists"
    const val CUSTOM_RULES = "custom_rules"
    const val DIAGNOSTICS = "diagnostics"
    const val BATTERY_PERFORMANCE = "battery_performance"
    const val PRIVACY_POLICY = "privacy_policy"
    const val ABOUT = "about"
}

private data class TopLevelDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val topLevelDestinations = listOf(
    TopLevelDestination(AdZeroRoutes.HOME, "Home", Icons.Filled.Home, Icons.Outlined.Home),
    TopLevelDestination(AdZeroRoutes.APPS, "Apps", Icons.Filled.Apps, Icons.Outlined.Apps),
    TopLevelDestination(AdZeroRoutes.GHOST, "Ghost", Icons.Filled.VisibilityOff, Icons.Outlined.VisibilityOff),
    TopLevelDestination(AdZeroRoutes.ACTIVITY, "Activity", Icons.Filled.BarChart, Icons.Outlined.BarChart),
    TopLevelDestination(AdZeroRoutes.SETTINGS, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
)

@Composable
fun AdZeroAppRoot(
    container: AppContainer,
    onToggleVpnProtection: () -> Unit,
    onPauseOrResumeVpn: () -> Unit
) {
    val factory = AdZeroViewModelFactory(container)
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val homeVm: HomeViewModel = viewModel(factory = factory)
    val appsVm: AppsViewModel = viewModel(factory = factory)
    val ghostVm: GhostViewModel = viewModel(factory = factory)
    val activityVm: ActivityViewModel = viewModel(factory = factory)
    val smartDnsVm: SmartDnsViewModel = viewModel(factory = factory)
    val settingsVm: SettingsViewModel = viewModel(factory = factory)

    val homeState by homeVm.uiState.collectAsStateWithLifecycle()
    val appsState by appsVm.uiState.collectAsStateWithLifecycle()
    val ghostState by ghostVm.uiState.collectAsStateWithLifecycle()
    val activityState by activityVm.uiState.collectAsStateWithLifecycle()
    val smartDnsState by smartDnsVm.uiState.collectAsStateWithLifecycle()
    val settingsState by settingsVm.uiState.collectAsStateWithLifecycle()

    val isTopLevel = topLevelDestinations.any { dest ->
        currentDestination?.hierarchy?.any { it.route == dest.route } == true
    }

    fun navigateTopLevel(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (!useNavRail && isTopLevel) {
                    NavigationBar(containerColor = AdZeroWhite) {
                        topLevelDestinations.forEach { dest ->
                            val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTopLevel(dest.route) },
                                modifier = Modifier.testTag("nav_${dest.route}"),
                                icon = {
                                    NavIconWithOptionalBadge(
                                        dest = dest,
                                        selected = selected,
                                        unreadGhostCount = homeState.unreadGhostCount
                                    )
                                },
                                label = { Text(dest.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = AdZeroPurple,
                                    selectedTextColor = AdZeroPurple,
                                    indicatorColor = AdZeroLavenderSurface,
                                    unselectedIconColor = AdZeroMutedText,
                                    unselectedTextColor = AdZeroMutedText
                                )
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail && isTopLevel) {
                    NavigationRail(containerColor = AdZeroWhite, modifier = Modifier.fillMaxHeight()) {
                        topLevelDestinations.forEach { dest ->
                            val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTopLevel(dest.route) },
                                modifier = Modifier.testTag("nav_rail_${dest.route}"),
                                icon = {
                                    NavIconWithOptionalBadge(
                                        dest = dest,
                                        selected = selected,
                                        unreadGhostCount = homeState.unreadGhostCount
                                    )
                                },
                                label = { Text(dest.label) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = AdZeroPurple,
                                    selectedTextColor = AdZeroPurple,
                                    indicatorColor = AdZeroLavenderSurface,
                                    unselectedIconColor = AdZeroMutedText,
                                    unselectedTextColor = AdZeroMutedText
                                )
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = AdZeroRoutes.HOME,
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 760.dp)
                    ) {
                        composable(AdZeroRoutes.HOME) {
                            HomeScreen(
                                uiState = homeState,
                                installedAppsRepository = homeVm.installedAppsRepository,
                                onToggleProtection = onToggleVpnProtection,
                                onPauseOrResume = onPauseOrResumeVpn,
                                onToggleAppInternet = homeVm::toggleAppInternetAccess,
                                onRefreshTraffic = homeVm::refreshTrafficStats,
                                onOpenSettings = { navigateTopLevel(AdZeroRoutes.SETTINGS) },
                                onOpenApps = { navigateTopLevel(AdZeroRoutes.APPS) },
                                onOpenGhost = { navigateTopLevel(AdZeroRoutes.GHOST) },
                                onOpenActivity = { navigateTopLevel(AdZeroRoutes.ACTIVITY) },
                                onOpenSmartDns = { navController.navigate(AdZeroRoutes.SMART_DNS) },
                                onOpenYouTube = { navController.navigate(AdZeroRoutes.YOUTUBE_ASSISTANT) },
                                onOpenBlocklists = { navController.navigate(AdZeroRoutes.BLOCKLISTS) }
                            )
                        }
                        composable(AdZeroRoutes.APPS) {
                            AppsControlScreen(
                                uiState = appsState,
                                installedAppsRepository = appsVm.installedAppsRepository,
                                onSearchChange = appsVm::setSearchQuery,
                                onFilterChange = appsVm::setFilter,
                                onSortChange = appsVm::setSortOrder,
                                onToggleSystemApps = appsVm::setShowSystemApps,
                                onUpdateAppRule = appsVm::updateAppFirewall,
                                onToggleStrictIsolation = appsVm::toggleStrictPacketIsolation,
                                onRefreshApps = appsVm::refreshInstalledApps,
                                onDismissBanner = appsVm::clearBanner
                            )
                        }
                        composable(AdZeroRoutes.GHOST) {
                            GhostInboxScreen(
                                uiState = ghostState,
                                onToggleGhostMode = { enabled -> ghostVm.toggleGhostMode(enabled) },
                                onToggleStrictIsolation = ghostVm::toggleStrictIsolation,
                                onToggleInboxCapture = ghostVm::toggleInboxCapture,
                                onTogglePreviewMask = ghostVm::togglePreviewPrivacyMasked,
                                onToggleBiometricLock = ghostVm::toggleBiometricLock,
                                onSearchChange = ghostVm::setSearchQuery,
                                onSelectContact = ghostVm::selectContact,
                                onMarkRead = ghostVm::markRead,
                                onMarkAllRead = ghostVm::markAllRead,
                                onDeleteMessage = ghostVm::deleteMessage,
                                onClearInbox = ghostVm::clearInbox,
                                onOpenScheduler = { navController.navigate(AdZeroRoutes.GHOST_SCHEDULER) },
                                onUnlocked = { ghostVm.setUnlocked(true) },
                                onRefreshPermissions = ghostVm::refreshPermissions
                            )
                        }
                        composable(AdZeroRoutes.GHOST_SCHEDULER) {
                            GhostSchedulerScreen(
                                uiState = ghostState,
                                onBack = { navController.popBackStack() },
                                onSaveSchedule = ghostVm::saveSchedule,
                                onToggleSchedule = ghostVm::toggleScheduleEnabled,
                                onDeleteSchedule = ghostVm::deleteSchedule,
                                onClearManualOverride = ghostVm::clearManualOverride
                            )
                        }
                        composable(AdZeroRoutes.ACTIVITY) {
                            ActivityAnalyticsScreen(
                                uiState = activityState,
                                installedAppsRepository = activityVm.installedAppsRepository,
                                onSelectRange = activityVm::setRange,
                                onSelectActionFilter = activityVm::setActionFilter,
                                onSearchChange = activityVm::setSearchQuery,
                                onSetRetentionDays = activityVm::setRetentionDays,
                                onQuickAllowDomain = activityVm::quickAllowDomain,
                                onQuickBlockDomain = activityVm::quickBlockDomain,
                                onClearHistory = activityVm::clearHistory,
                                onExportCsv = activityVm::generateCsvContent
                            )
                        }
                        composable(AdZeroRoutes.SETTINGS) {
                            SettingsScreen(
                                uiState = settingsState,
                                onToggleAdBlocking = settingsVm::setAdBlocking,
                                onToggleTrackerBlocking = settingsVm::setTrackerBlocking,
                                onToggleMalwareBlocking = settingsVm::setMalwareBlocking,
                                onToggleStrictMode = settingsVm::setStrictMode,
                                onToggleAutoStart = settingsVm::setAutoStartOnBoot,
                                onOpenBlocklists = { navController.navigate(AdZeroRoutes.BLOCKLISTS) },
                                onOpenSmartDns = { navController.navigate(AdZeroRoutes.SMART_DNS) },
                                onOpenCustomRules = { navController.navigate(AdZeroRoutes.CUSTOM_RULES) },
                                onOpenDiagnostics = { navController.navigate(AdZeroRoutes.DIAGNOSTICS) },
                                onOpenGhost = { navigateTopLevel(AdZeroRoutes.GHOST) },
                                onOpenGhostScheduler = { navController.navigate(AdZeroRoutes.GHOST_SCHEDULER) },
                                onOpenYouTube = { navController.navigate(AdZeroRoutes.YOUTUBE_ASSISTANT) },
                                onOpenBattery = { navController.navigate(AdZeroRoutes.BATTERY_PERFORMANCE) },
                                onOpenPrivacyPolicy = { navController.navigate(AdZeroRoutes.PRIVACY_POLICY) },
                                onOpenAbout = { navController.navigate(AdZeroRoutes.ABOUT) },
                                onClearCache = settingsVm::clearDnsCache,
                                onClearAllData = settingsVm::clearAllLocalData,
                                onExportCsv = settingsVm::generateCsvContent,
                                onFeedback = settingsVm::setFeedback
                            )
                        }
                        composable(AdZeroRoutes.SMART_DNS) {
                            SmartDnsScreen(
                                uiState = smartDnsState,
                                onBack = { navController.popBackStack() },
                                onSaveConfig = smartDnsVm::selectProviderAndProtocol,
                                onResetDefault = smartDnsVm::resetToDefault,
                                onTestConnection = smartDnsVm::testConnection,
                                onClearCache = smartDnsVm::clearDnsCache
                            )
                        }
                        composable(AdZeroRoutes.YOUTUBE_ASSISTANT) {
                            YouTubeAssistantScreen(
                                uiState = settingsState,
                                onBack = { navController.popBackStack() },
                                onSetConsent = settingsVm::setYouTubeConsent,
                                onSetEnabled = settingsVm::setYouTubeSkipEnabled,
                                onResetStats = settingsVm::resetYouTubeStats,
                                onRefreshStatus = settingsVm::refreshDiagnostics
                            )
                        }
                        composable(AdZeroRoutes.BLOCKLISTS) {
                            BlocklistsScreen(
                                uiState = settingsState,
                                onBack = { navController.popBackStack() },
                                onToggleAutoUpdate = settingsVm::setAutoBlocklistUpdate,
                                onToggleSource = settingsVm::toggleBlocklistSource,
                                onManualUpdate = settingsVm::runManualBlocklistUpdate
                            )
                        }
                        composable(AdZeroRoutes.CUSTOM_RULES) {
                            CustomRulesScreen(
                                uiState = settingsState,
                                onBack = { navController.popBackStack() },
                                onAddRule = settingsVm::addCustomRule,
                                onDeleteRule = settingsVm::deleteCustomRule
                            )
                        }
                        composable(AdZeroRoutes.DIAGNOSTICS) {
                            DiagnosticsScreen(
                                uiState = settingsState,
                                onBack = { navController.popBackStack() },
                                onRefresh = settingsVm::refreshDiagnostics
                            )
                        }
                        composable(AdZeroRoutes.BATTERY_PERFORMANCE) {
                            BatteryPerformanceScreen(
                                uiState = settingsState,
                                onBack = { navController.popBackStack() },
                                onClearCache = settingsVm::clearDnsCache
                            )
                        }
                        composable(AdZeroRoutes.PRIVACY_POLICY) {
                            PrivacyPolicyScreen(onBack = { navController.popBackStack() })
                        }
                        composable(AdZeroRoutes.ABOUT) {
                            AboutAdZeroScreen(onBack = { navController.popBackStack() })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NavIconWithOptionalBadge(
    dest: TopLevelDestination,
    selected: Boolean,
    unreadGhostCount: Int
) {
    val iconVector = if (selected) dest.selectedIcon else dest.unselectedIcon
    if (dest.route == AdZeroRoutes.GHOST && unreadGhostCount > 0) {
        BadgedBox(
            badge = {
                Badge(containerColor = AdZeroPurple, contentColor = AdZeroWhite) {
                    Text(if (unreadGhostCount > 99) "99+" else unreadGhostCount.toString())
                }
            }
        ) {
            Icon(iconVector, contentDescription = dest.label)
        }
    } else {
        Icon(iconVector, contentDescription = dest.label)
    }
}
