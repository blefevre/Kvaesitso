package de.mm20.launcher2.ui.settings.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun SmartFavoritesDebugDialog(
    onDismiss: () -> Unit
) {
    val viewModel: SmartFavoritesDebugViewModel = viewModel()
    val currentContext by viewModel.currentContext.collectAsState()
    val appSuggestions by viewModel.appSuggestions.collectAsState()
    val analytics by viewModel.analytics.collectAsState()
    
    LaunchedEffect(Unit) {
        viewModel.loadDebugData()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Smart Favorites Debug")
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    currentContext?.let { context ->
                        DebugContextCard(context)
                    }
                }
                
                item {
                    analytics?.let { stats ->
                        DebugStatsCard(stats)
                    }
                }
                
                item {
                    Text(
                        text = "Top App Suggestions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                items(appSuggestions.take(5)) { suggestion ->
                    DebugSuggestionCard(suggestion)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.refreshCurrentContext() }) {
                Text("Refresh")
            }
        }
    )
}

@Composable
fun DebugContextCard(context: de.mm20.launcher2.searchable.debug.ContextDebugInfo) {
    Card {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Current Context",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = context.contextSummary,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun DebugStatsCard(analytics: de.mm20.launcher2.searchable.debug.SmartFavoritesAnalytics) {
    Card {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "System Stats",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            DebugStatRow("Total Apps", analytics.totalApps.toString())
            DebugStatRow("Smart Apps", analytics.appsWithSmartData.toString())
            DebugStatRow("Avg History", "%.1f".format(analytics.averageContextHistorySize))
        }
    }
}

@Composable
fun DebugSuggestionCard(suggestion: de.mm20.launcher2.searchable.debug.AppSuggestionDebugInfo) {
    Card {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = suggestion.appName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "%.3f".format(suggestion.finalScore),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = suggestion.reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun DebugStatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace
        )
    }
}