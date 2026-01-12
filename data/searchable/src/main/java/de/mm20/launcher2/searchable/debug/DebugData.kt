package de.mm20.launcher2.searchable.debug

import de.mm20.launcher2.searchable.context.ContextData
import de.mm20.launcher2.searchable.context.TimeSlot
import kotlinx.serialization.Serializable

/**
 * Debug data models for visualizing smart favorites context analysis.
 */

@Serializable
data class AppDebugInfo(
    val key: String,
    val name: String,
    val launchCount: Int,
    val weight: Double,
    val contextHistory: List<ContextData>,
    val patterns: List<UsagePattern>,
    val currentContextScore: Double,
    val lastLaunched: Long?
)

@Serializable
data class UsagePattern(
    val type: PatternType,
    val description: String,
    val strength: Double, // 0.0 to 1.0
    val occurrences: Int,
    val details: String
)

@Serializable
enum class PatternType {
    TIME_OF_DAY,
    DAY_OF_WEEK,
    WIFI_NETWORK,
    BLUETOOTH_DEVICE,
    BATTERY_STATE,
    DEVICE_ORIENTATION,
    TIME_SLOT
}

@Serializable
data class ContextDebugInfo(
    val currentContext: ContextData,
    val timestamp: Long,
    val contextSummary: String
)

@Serializable
data class SmartFavoritesAnalytics(
    val totalApps: Int,
    val appsWithSmartData: Int,
    val averageContextHistorySize: Double,
    val mostActiveTimeSlot: TimeSlot?,
    val topWifiNetworks: List<NetworkUsage>,
    val topBluetoothCategories: List<String>,
    val batteryUsageDistribution: Map<String, Int>, // charging/not charging counts
    val orientationUsage: Map<String, Int>
)

@Serializable
data class NetworkUsage(
    val ssid: String,
    val usageCount: Int,
    val uniqueApps: Int
)

@Serializable
data class AppSuggestionDebugInfo(
    val appKey: String,
    val appName: String,
    val baseWeight: Double,
    val contextBoost: Double,
    val finalScore: Double,
    val matchingPatterns: List<String>,
    val contextSimilarity: Double,
    val reason: String
)

@Serializable
data class DebugSession(
    val sessionId: String,
    val startTime: Long,
    val currentContext: ContextDebugInfo,
    val suggestedApps: List<AppSuggestionDebugInfo>,
    val analytics: SmartFavoritesAnalytics,
    val notes: String = ""
)