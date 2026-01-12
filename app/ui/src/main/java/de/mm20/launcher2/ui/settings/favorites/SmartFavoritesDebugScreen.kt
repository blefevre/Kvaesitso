package de.mm20.launcher2.ui.settings.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.searchable.debug.AppDebugInfo
import de.mm20.launcher2.searchable.debug.AppSuggestionDebugInfo
import de.mm20.launcher2.searchable.debug.SmartFavoritesAnalytics
import de.mm20.launcher2.searchable.debug.UsagePattern
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Serializable
data object SmartFavoritesDebugRoute : NavKey

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartFavoritesDebugScreen() {
    val viewModel: SmartFavoritesDebugViewModel = viewModel()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Overview", "Apps", "Suggestions", "KNN")
    
    LaunchedEffect(errorMessage) {
        errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.dismissError()
        }
    }
    
    // Automatically request permissions when opening debug screen
    SmartFavoritesPermissionHandler(
        enabled = true, // Always check permissions when in debug screen
        onAllPermissionsGranted = {
            // Refresh data after permissions are granted
            viewModel.refreshCurrentContext()
        }
    ) {
        // Empty content - we just want the permission handling
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Smart Favorites Debug") }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { 
                    if (selectedTab == 0) {
                        viewModel.loadDebugData()
                    } else {
                        viewModel.refreshCurrentContext()
                    }
                }
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }
            
            when (selectedTab) {
                0 -> OverviewTab(viewModel)
                1 -> AppsTab(viewModel)
                2 -> SuggestionsTab(viewModel)
                3 -> KNNTab(viewModel)
            }
        }
    }
}

@Composable
fun OverviewTab(viewModel: SmartFavoritesDebugViewModel) {
    val currentContext by viewModel.currentContext.collectAsState()
    val analytics by viewModel.analytics.collectAsState()
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            currentContext?.let { context ->
                CurrentContextVectorCard(context)
            }
        }
        
        item {
            analytics?.let { analyticsData ->
                QuickStatsCard(analyticsData)
            }
        }
        
        item {
            val topApps = viewModel.getTopAppsForCurrentContext()
            if (topApps.isNotEmpty()) {
                TopAppsForContextCard(topApps)
            }
        }
    }
}

@Composable
fun AppsTab(viewModel: SmartFavoritesDebugViewModel) {
    val allApps by viewModel.allAppsDebugInfo.collectAsState()
    val selectedApp by viewModel.selectedApp.collectAsState()
    
    if (selectedApp != null) {
        AppDetailView(selectedApp!!, onBack = { viewModel.clearSelectedApp() })
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(allApps) { app ->
                AppCard(app) {
                    viewModel.selectApp(app)
                }
            }
        }
    }
}

@Composable
fun SuggestionsTab(viewModel: SmartFavoritesDebugViewModel) {
    val suggestions by viewModel.appSuggestions.collectAsState()
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(suggestions) { suggestion ->
            SuggestionCard(suggestion)
        }
    }
}


@Composable
fun CurrentContextVectorCard(context: de.mm20.launcher2.searchable.debug.ContextDebugInfo) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Current Context Vector",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = "Time Dimensions",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            
            context.currentContext.timeContext?.let { timeCtx ->
                ContextDetailRow("Hour", "${timeCtx.hour}:00")
                ContextDetailRow("Day of Week", getDayName(timeCtx.dayOfWeek))
                ContextDetailRow("Time Slot", timeCtx.timeSlot.name.lowercase().replace("_", " "))
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Network Dimensions",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            
            context.currentContext.networkContext?.let { netCtx ->
                ContextDetailRow("Connection Type", netCtx.connectionType.name)
                ContextDetailRow("Is Connected", if (netCtx.isConnected) "Yes" else "No")
                if (netCtx.wifiSsid != null) {
                    ContextDetailRow("WiFi SSID", netCtx.wifiSsid!!)
                } else if (netCtx.connectionType == de.mm20.launcher2.searchable.context.ConnectionType.WIFI) {
                    ContextDetailRow("WiFi SSID", "Not available (permissions?)")
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Bluetooth Dimensions",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            
            context.currentContext.bluetoothContext?.let { btCtx ->
                if (btCtx.connectedDevices.isNotEmpty()) {
                    ContextDetailRow("Connected Devices", "${btCtx.connectedDevices.size}")
                    btCtx.connectedDevices.take(3).forEach { deviceName ->
                        ContextDetailRow("  Device", deviceName)
                    }
                    if (btCtx.connectedDevices.size > 3) {
                        ContextDetailRow("  ...", "and ${btCtx.connectedDevices.size - 3} more")
                    }
                } else {
                    ContextDetailRow("Connected Devices", "None")
                }
                
                if (btCtx.deviceCategories.isNotEmpty()) {
                    ContextDetailRow("Device Types", btCtx.deviceCategories.joinToString(", ") { it.name.lowercase() })
                }
            } ?: run {
                ContextDetailRow("Bluetooth Status", "Not available")
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Device Dimensions", 
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            
            context.currentContext.deviceContext?.let { deviceCtx ->
                ContextDetailRow("Charging", if (deviceCtx.isCharging) "Yes" else "No")
                ContextDetailRow("Orientation", deviceCtx.orientation.name.lowercase())
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Updated: ${formatTimestamp(context.timestamp)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun QuickStatsCard(analytics: SmartFavoritesAnalytics) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Quick Stats",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            StatRow("Total Apps", analytics.totalApps.toString())
            StatRow("Apps with Smart Data", analytics.appsWithSmartData.toString())
            StatRow("Avg Context History", "%.1f entries".format(analytics.averageContextHistorySize))
            
            analytics.mostActiveTimeSlot?.let { timeSlot ->
                StatRow("Most Active Time", timeSlot.name.lowercase().replace("_", " "))
            }
        }
    }
}

@Composable
fun TopAppsForContextCard(topApps: List<AppDebugInfo>) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Top Apps for Current Context",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            topApps.take(5).forEach { app ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = app.name,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Score: %.2f".format(app.currentContextScore),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
fun AppCard(app: AppDebugInfo, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = app.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Weight: %.3f".format(app.weight),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row {
                Text(
                    text = "Launches: ${app.launchCount}",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Patterns: ${app.patterns.size}",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "History: ${app.contextHistory.size}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun AppDetailView(app: AppDebugInfo, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Back button and title row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Back to app list"
                )
            }
            Text(
                text = app.name,
                style = MaterialTheme.typography.headlineMedium
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Stats
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                StatRow("Launch Count", app.launchCount.toString())
                StatRow("Base Weight", "%.6f".format(app.weight))
                StatRow("Context Score", "%.6f".format(app.currentContextScore))
                StatRow("History Entries", app.contextHistory.size.toString())
                app.lastLaunched?.let {
                    StatRow("Last Launched", formatTimestamp(it))
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Patterns
        if (app.patterns.isNotEmpty()) {
            Text(
                text = "Usage Patterns",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            app.patterns.forEach { pattern ->
                PatternCard(pattern)
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // Context History
        if (app.contextHistory.isNotEmpty()) {
            Text(
                text = "Context History (${app.contextHistory.size} entries)",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            // Sort by most recent first
            app.contextHistory.sortedByDescending { it.timestamp }.forEach { contextEntry ->
                ContextHistoryCard(contextEntry)
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

@Composable
fun SuggestionCard(suggestion: AppSuggestionDebugInfo) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = suggestion.appName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "%.3f".format(suggestion.finalScore),
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = suggestion.reason,
                style = MaterialTheme.typography.bodyMedium
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row {
                Text(
                    text = "Base: %.3f".format(suggestion.baseWeight),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Boost: %+.3f".format(suggestion.contextBoost),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = if (suggestion.contextBoost > 0) Color.Green else Color.Red
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Row {
                Text(
                    text = "KNN Sim: %.2f".format(suggestion.contextSimilarity),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(16.dp))
                if (suggestion.reason.contains("KNN match")) {
                    Text(
                        text = "✓ KNN",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Green,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text(
                        text = "× KNN", 
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Red
                    )
                }
            }
        }
    }
}

@Composable
fun PatternCard(pattern: UsagePattern) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = pattern.description,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${(pattern.strength * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            LinearProgressIndicator(
                progress = pattern.strength.toFloat(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = pattern.details,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}


@Composable
fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun ContextHistoryCard(contextData: de.mm20.launcher2.searchable.context.ContextData) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatTimestamp(contextData.timestamp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Entry ${contextData.hashCode().toString().takeLast(4)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Time context
            contextData.timeContext?.let { timeCtx ->
                ContextDetailRow(
                    "Time", 
                    "${timeCtx.hour}:00 (${timeCtx.timeSlot.name.lowercase().replace("_", " ")})"
                )
                ContextDetailRow("Day", getDayName(timeCtx.dayOfWeek))
            }
            
            // Network context
            contextData.networkContext?.let { netCtx ->
                when {
                    netCtx.wifiSsid != null -> {
                        val ssid = netCtx.wifiSsid
                        if (ssid != null) {
                            ContextDetailRow("WiFi", ssid)
                        }
                    }
                    netCtx.connectionType.name == "MOBILE" -> ContextDetailRow("Network", "Mobile data")
                    else -> ContextDetailRow("Network", "No connection")
                }
            }
            
            // Bluetooth context
            contextData.bluetoothContext?.let { btCtx ->
                if (btCtx.connectedDevices.isNotEmpty()) {
                    ContextDetailRow("Bluetooth", "${btCtx.connectedDevices.size} device(s)")
                }
                if (btCtx.deviceCategories.isNotEmpty()) {
                    ContextDetailRow(
                        "BT Categories", 
                        btCtx.deviceCategories.joinToString(", ") { it.name.lowercase() }
                    )
                }
            }
            
            // Device context
            contextData.deviceContext?.let { deviceCtx ->
                val deviceInfo = mutableListOf<String>()
                if (deviceCtx.isCharging) {
                    deviceInfo.add("charging")
                }
                deviceInfo.add(deviceCtx.orientation.name.lowercase())
                
                if (deviceInfo.isNotEmpty()) {
                    ContextDetailRow("Device", deviceInfo.joinToString(" • "))
                }
            }
        }
    }
}

@Composable
fun ContextDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace
        )
    }
}

fun getDayName(dayOfWeek: Int): String {
    return when (dayOfWeek) {
        1 -> "Monday"
        2 -> "Tuesday" 
        3 -> "Wednesday"
        4 -> "Thursday"
        5 -> "Friday"
        6 -> "Saturday"
        7 -> "Sunday"
        else -> "Unknown"
    }
}

@Composable
fun KNNTab(viewModel: SmartFavoritesDebugViewModel) {
    val suggestions by viewModel.appSuggestions.collectAsState()
    val currentContext by viewModel.currentContext.collectAsState()
    val knnConfig by viewModel.knnConfiguration.collectAsState()
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // KNN Configuration
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "KNN Configuration",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    StatRow("Algorithm", "K-Nearest Neighbors")
                    StatRow("K Value", "${knnConfig.first} neighbors")
                    StatRow("Alpha (KNN weight)", "${knnConfig.second}")
                    StatRow("Vector Dimensions", "15")
                    StatRow("Distance Metric", "Weighted Euclidean")
                }
            }
        }
        
        
        // Top KNN Matches
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Top KNN Matches",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    suggestions.filter { it.reason.contains("KNN match") }
                        .take(10)
                        .forEachIndexed { index, suggestion ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${index + 1}. ${suggestion.appName}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = suggestion.reason,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = "%.3f".format(suggestion.finalScore),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (index < 9) {
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                        
                    if (suggestions.none { it.reason.contains("KNN match") }) {
                        Text(
                            text = "No KNN matches found",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        
        // Algorithm Performance
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Algorithm Performance",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    val knnMatches = suggestions.count { it.reason.contains("KNN match") }
                    val totalSuggestions = suggestions.size
                    val knnEffectiveness = if (totalSuggestions > 0) {
                        (knnMatches.toDouble() / totalSuggestions * 100).toInt()
                    } else 0
                    
                    StatRow("Total Suggestions", totalSuggestions.toString())
                    StatRow("KNN Matches", knnMatches.toString())
                    StatRow("KNN Effectiveness", "$knnEffectiveness%")
                    
                    val avgKnnScore = suggestions.filter { it.reason.contains("KNN match") }
                        .map { it.contextSimilarity }
                        .average()
                        .takeIf { !it.isNaN() } ?: 0.0
                    StatRow("Avg KNN Similarity", "%.3f".format(avgKnnScore))
                }
            }
        }
        
        // Network Troubleshooting
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Network Detection Troubleshooting",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    currentContext?.let { context ->
                        val netCtx = context.currentContext.networkContext
                        
                        if (netCtx?.connectionType == de.mm20.launcher2.searchable.context.ConnectionType.NONE) {
                            Text(
                                text = "⚠️ No network connection detected",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = "• Check if you're actually connected to WiFi or mobile data",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "• Check app permissions for network access",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else if (netCtx?.connectionType == de.mm20.launcher2.searchable.context.ConnectionType.WIFI && netCtx.wifiSsid == null) {
                            Text(
                                text = "⚠️ WiFi detected but SSID unavailable",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = "• Android 10+ requires location permission to access WiFi SSID",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "• Check that location services are enabled",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            LocationPermissionHandler(
                                onPermissionGranted = {
                                    // Refresh context data after permission is granted
                                    viewModel.refreshCurrentContext()
                                },
                                onPermissionDenied = {
                                    // Handle permission denial if needed
                                }
                            ) { requestPermission, hasPermission ->
                                if (!hasPermission) {
                                    Button(
                                        onClick = requestPermission,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Grant Location Permission")
                                    }
                                }
                            }
                        } else if (netCtx?.connectionType == de.mm20.launcher2.searchable.context.ConnectionType.WIFI && netCtx.wifiSsid != null) {
                            Text(
                                text = "✅ WiFi detection working correctly",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Green
                            )
                            Text(
                                text = "SSID: ${netCtx.wifiSsid}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else if (netCtx?.connectionType == de.mm20.launcher2.searchable.context.ConnectionType.MOBILE) {
                            Text(
                                text = "📱 Mobile data connection detected",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    } ?: run {
                        Text(
                            text = "❌ No context data available",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
        
        // Bluetooth Troubleshooting
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Bluetooth Detection Troubleshooting",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Always show permission status for debugging
                    BluetoothPermissionHandler(
                        onPermissionGranted = {
                            viewModel.refreshCurrentContext()
                        },
                        onPermissionDenied = {
                            // Handle denial
                        }
                    ) { requestPermission, hasPermission ->
                        Text(
                            text = "Bluetooth Permission Status: ${if (hasPermission) "✅ GRANTED" else "❌ MISSING"}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            color = if (hasPermission) Color.Green else Color.Red
                        )
                        
                        if (!hasPermission) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = requestPermission,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Grant Bluetooth Permission")
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    currentContext?.let { context ->
                        val btCtx = context.currentContext.bluetoothContext
                        
                        if (btCtx?.connectedDevices?.isNotEmpty() == true) {
                            Text(
                                text = "✅ Bluetooth detection working correctly",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Green
                            )
                            Text(
                                text = "Connected devices: ${btCtx.connectedDevices.joinToString(", ")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else if (btCtx != null) {
                            Text(
                                text = "⚠️ No Bluetooth devices detected",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = "• Check if Bluetooth is enabled on your device",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "• Make sure you have connected Bluetooth devices (headphones, speakers, etc.)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "• Check app permissions for Bluetooth access",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            // Check permission status directly since context might be null due to missing permissions
                            BluetoothPermissionHandler(
                                onPermissionGranted = {
                                    // Refresh context data after permission is granted
                                    viewModel.refreshCurrentContext()
                                },
                                onPermissionDenied = {
                                    // Handle permission denial if needed
                                }
                            ) { requestPermission, hasPermission ->
                                if (hasPermission) {
                                    Text(
                                        text = "⚠️ Bluetooth context unavailable",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        text = "• Bluetooth may be disabled or no devices connected",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    Text(
                                        text = "❌ Bluetooth permissions missing",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        text = "• Bluetooth permissions are required to detect connected devices",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    Button(
                                        onClick = requestPermission,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Grant Bluetooth Permission")
                                    }
                                }
                            }
                        }
                    } ?: run {
                        // No context data - could be due to missing permissions or other issues
                        BluetoothPermissionHandler(
                            onPermissionGranted = {
                                viewModel.refreshCurrentContext()
                            },
                            onPermissionDenied = {
                                // Handle permission denial if needed
                            }
                        ) { requestPermission, hasPermission ->
                            if (hasPermission) {
                                Text(
                                    text = "❌ No context data available",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = "• Context collection may have failed",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Text(
                                    text = "❌ Bluetooth permissions missing",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = "• Grant Bluetooth permissions to enable context detection",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                Button(
                                    onClick = requestPermission,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Grant Bluetooth Permission")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun formatTimestamp(timestamp: Long): String {
    val date = Date(timestamp)
    val formatter = SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault())
    return formatter.format(date)
}