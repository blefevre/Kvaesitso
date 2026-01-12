package de.mm20.launcher2.searchable.context

import kotlinx.serialization.Serializable
import java.time.DayOfWeek

/**
 * Represents the context in which an app was launched.
 * This data is used for smart favorites to suggest relevant apps based on current conditions.
 */
@Serializable
data class ContextData(
    val timestamp: Long = System.currentTimeMillis(),
    val timeContext: TimeContext? = null,
    val networkContext: NetworkContext? = null,
    val bluetoothContext: BluetoothContext? = null,
    val deviceContext: DeviceContext? = null
) {
    /**
     * Calculate similarity between this context and another context.
     * Returns a score between 0.0 (no similarity) and 1.0 (identical).
     */
    fun similarityScore(other: ContextData): Double {
        var totalWeight = 0.0
        var matchingWeight = 0.0

        timeContext?.let { time ->
            other.timeContext?.let { otherTime ->
                totalWeight += TIME_WEIGHT
                matchingWeight += TIME_WEIGHT * time.similarityScore(otherTime)
            }
        }

        networkContext?.let { network ->
            other.networkContext?.let { otherNetwork ->
                totalWeight += NETWORK_WEIGHT
                matchingWeight += NETWORK_WEIGHT * network.similarityScore(otherNetwork)
            }
        }

        bluetoothContext?.let { bluetooth ->
            other.bluetoothContext?.let { otherBluetooth ->
                totalWeight += BLUETOOTH_WEIGHT
                matchingWeight += BLUETOOTH_WEIGHT * bluetooth.similarityScore(otherBluetooth)
            }
        }

        deviceContext?.let { device ->
            other.deviceContext?.let { otherDevice ->
                totalWeight += DEVICE_WEIGHT
                matchingWeight += DEVICE_WEIGHT * device.similarityScore(otherDevice)
            }
        }

        return if (totalWeight > 0) matchingWeight / totalWeight else 0.0
    }

    companion object {
        private const val TIME_WEIGHT = 0.4
        private const val NETWORK_WEIGHT = 0.3
        private const val BLUETOOTH_WEIGHT = 0.2
        private const val DEVICE_WEIGHT = 0.1
    }
}

@Serializable
data class TimeContext(
    val hour: Int, // 0-23
    val dayOfWeek: Int, // DayOfWeek.getValue() (1=Monday, 7=Sunday)
    val timeSlot: TimeSlot
) {
    fun similarityScore(other: TimeContext): Double {
        val hourScore = calculateHourSimilarity(hour, other.hour)
        val dayScore = if (dayOfWeek == other.dayOfWeek) 1.0 else 0.0
        val slotScore = if (timeSlot == other.timeSlot) 1.0 else 0.0

        return (hourScore * 0.5 + dayScore * 0.3 + slotScore * 0.2)
    }

    private fun calculateHourSimilarity(hour1: Int, hour2: Int): Double {
        val diff = minOf(
            kotlin.math.abs(hour1 - hour2),
            24 - kotlin.math.abs(hour1 - hour2) // Consider wrap-around (23 and 0 are close)
        )
        return when (diff) {
            0 -> 1.0
            1 -> 0.8
            2 -> 0.6
            3 -> 0.4
            else -> 0.0
        }
    }
}

@Serializable
enum class TimeSlot {
    EARLY_MORNING, // 5-8
    MORNING,       // 9-11
    MIDDAY,        // 12-14
    AFTERNOON,     // 15-17
    EVENING,       // 18-20
    NIGHT,         // 21-23
    LATE_NIGHT     // 0-4
}

@Serializable
data class NetworkContext(
    val wifiSsid: String? = null,
    val isConnected: Boolean = false,
    val connectionType: ConnectionType
) {
    fun similarityScore(other: NetworkContext): Double {
        return when {
            wifiSsid != null && other.wifiSsid != null -> {
                if (wifiSsid == other.wifiSsid) 1.0 else 0.0
            }
            connectionType == other.connectionType -> 0.5
            else -> 0.0
        }
    }
}

@Serializable
enum class ConnectionType {
    WIFI,
    MOBILE,
    NONE
}

@Serializable
data class BluetoothContext(
    val connectedDevices: Set<String> = emptySet(),
    val deviceCategories: Set<BluetoothCategory> = emptySet()
) {
    fun similarityScore(other: BluetoothContext): Double {
        val deviceOverlap = connectedDevices.intersect(other.connectedDevices).size.toDouble()
        val deviceUnion = connectedDevices.union(other.connectedDevices).size.toDouble()
        val deviceScore = if (deviceUnion > 0) deviceOverlap / deviceUnion else 0.0

        val categoryOverlap = deviceCategories.intersect(other.deviceCategories).size.toDouble()
        val categoryUnion = deviceCategories.union(other.deviceCategories).size.toDouble()
        val categoryScore = if (categoryUnion > 0) categoryOverlap / categoryUnion else 0.0

        return (deviceScore * 0.7 + categoryScore * 0.3)
    }
}

@Serializable
enum class BluetoothCategory {
    HEADPHONES,
    SPEAKERS,
    CAR,
    KEYBOARD,
    MOUSE,
    WATCH,
    FITNESS_TRACKER,
    OTHER
}

@Serializable
data class DeviceContext(
    val isCharging: Boolean = false,
    val orientation: Orientation = Orientation.PORTRAIT
) {
    fun similarityScore(other: DeviceContext): Double {
        val chargingScore = if (isCharging == other.isCharging) 1.0 else 0.0
        val orientationScore = if (orientation == other.orientation) 1.0 else 0.0
        
        return (chargingScore * 0.6 + orientationScore * 0.4)
    }
}

@Serializable
enum class Orientation {
    PORTRAIT,
    LANDSCAPE
}