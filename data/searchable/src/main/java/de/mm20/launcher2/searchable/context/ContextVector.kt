package de.mm20.launcher2.searchable.context

import kotlinx.serialization.Serializable
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Fixed-size vector representation of context data for efficient KNN search.
 * Each context is encoded into a standardized vector for distance calculations.
 */
@Serializable
data class ContextVector(
    // Time dimensions (3 values)
    val hour: Double,           // 0.0 to 23.0, normalized to 0.0-1.0
    val dayOfWeek: Double,      // 1.0 to 7.0, normalized to 0.0-1.0
    val timeSlot: Double,       // 0.0 to 6.0, normalized to 0.0-1.0 (7 time slots)
    
    // Network dimensions (2 values)
    val networkType: Double,    // 0.0=none, 0.5=mobile, 1.0=wifi
    val wifiNetworkHash: Double, // Hash of WiFi SSID, normalized to 0.0-1.0
    
    // Bluetooth dimensions (8 values)
    val hasBluetoothHeadphones: Double,    // 1.0 if headphones connected
    val hasBluetoothSpeakers: Double,      // 1.0 if speakers connected
    val hasBluetoothCar: Double,           // 1.0 if car connected
    val hasBluetoothKeyboard: Double,      // 1.0 if keyboard connected
    val hasBluetoothMouse: Double,         // 1.0 if mouse connected
    val hasBluetoothWatch: Double,         // 1.0 if watch connected
    val hasBluetoothFitness: Double,       // 1.0 if fitness tracker connected
    val bluetoothDeviceCount: Double,      // Number of devices, normalized to 0.0-1.0
    
    // Device dimensions (2 values)
    val isCharging: Double,     // 1.0 if charging, 0.0 otherwise
    val isPortrait: Double,     // 1.0 if portrait, 0.0 if landscape
    
    // Total: 15 dimensions
) {
    
    /**
     * Calculate Euclidean distance between this vector and another.
     * Lower distance = more similar contexts.
     */
    fun distanceTo(other: ContextVector): Double {
        val dimensions = listOf(
            // Time dimensions with higher weight
            (hour - other.hour) * TIME_WEIGHT,
            (dayOfWeek - other.dayOfWeek) * TIME_WEIGHT,
            (timeSlot - other.timeSlot) * TIME_SLOT_WEIGHT,
            
            // Network dimensions with medium weight
            (networkType - other.networkType) * NETWORK_WEIGHT,
            (wifiNetworkHash - other.wifiNetworkHash) * WIFI_WEIGHT,
            
            // Bluetooth dimensions with medium weight
            (hasBluetoothHeadphones - other.hasBluetoothHeadphones) * BLUETOOTH_WEIGHT,
            (hasBluetoothSpeakers - other.hasBluetoothSpeakers) * BLUETOOTH_WEIGHT,
            (hasBluetoothCar - other.hasBluetoothCar) * BLUETOOTH_WEIGHT,
            (hasBluetoothKeyboard - other.hasBluetoothKeyboard) * BLUETOOTH_WEIGHT,
            (hasBluetoothMouse - other.hasBluetoothMouse) * BLUETOOTH_WEIGHT,
            (hasBluetoothWatch - other.hasBluetoothWatch) * BLUETOOTH_WEIGHT,
            (hasBluetoothFitness - other.hasBluetoothFitness) * BLUETOOTH_WEIGHT,
            (bluetoothDeviceCount - other.bluetoothDeviceCount) * BLUETOOTH_WEIGHT,
            
            // Device dimensions with lower weight
            (isCharging - other.isCharging) * CHARGING_WEIGHT,
            (isPortrait - other.isPortrait) * ORIENTATION_WEIGHT,
        )
        
        return sqrt(dimensions.sumOf { it.pow(2) })
    }
    
    /**
     * Calculate similarity score (0.0 to 1.0, higher = more similar).
     * Uses exponential decay based on distance.
     */
    fun similarityTo(other: ContextVector): Double {
        val distance = distanceTo(other)
        // Convert distance to similarity with exponential decay
        return kotlin.math.exp(-distance * SIMILARITY_DECAY_RATE)
    }
    
    companion object {
        // Dimension weights for distance calculation
        private const val TIME_WEIGHT = 2.0          // Time is very important
        private const val TIME_SLOT_WEIGHT = 1.5     // Time slots moderately important
        private const val NETWORK_WEIGHT = 1.8       // Network context is important
        private const val WIFI_WEIGHT = 2.2          // Specific WiFi network very important
        private const val BLUETOOTH_WEIGHT = 1.3     // Bluetooth somewhat important
        private const val CHARGING_WEIGHT = 1.2      // Charging status moderately important
        private const val ORIENTATION_WEIGHT = 0.6   // Orientation least important
        
        private const val SIMILARITY_DECAY_RATE = 0.5 // How fast similarity decays with distance
        private const val MAX_BLUETOOTH_DEVICES = 10.0 // For normalization
        
        /**
         * Convert ContextData to ContextVector for KNN operations.
         */
        fun fromContextData(contextData: ContextData): ContextVector {
            // Encode time slot as a single value
            val timeSlotValue = when (contextData.timeContext?.timeSlot) {
                TimeSlot.EARLY_MORNING -> 0.0
                TimeSlot.MORNING -> 1.0
                TimeSlot.MIDDAY -> 2.0
                TimeSlot.AFTERNOON -> 3.0
                TimeSlot.EVENING -> 4.0
                TimeSlot.NIGHT -> 5.0
                TimeSlot.LATE_NIGHT -> 6.0
                null -> 3.0 // Default to afternoon
            }
            
            // Encode network type as a single value
            val networkTypeValue = when (contextData.networkContext?.connectionType) {
                ConnectionType.NONE -> 0.0
                ConnectionType.MOBILE -> 0.5
                ConnectionType.WIFI -> 1.0
                null -> 0.0
            }
            
            return ContextVector(
                // Time encoding
                hour = (contextData.timeContext?.hour?.toDouble() ?: 12.0) / 23.0,
                dayOfWeek = ((contextData.timeContext?.dayOfWeek?.toDouble() ?: 1.0) - 1.0) / 6.0,
                timeSlot = timeSlotValue / 6.0, // Normalize to 0.0-1.0
                
                // Network encoding
                networkType = networkTypeValue,
                wifiNetworkHash = normalizeStringHash(contextData.networkContext?.wifiSsid),
                
                // Bluetooth encoding
                hasBluetoothHeadphones = if (contextData.bluetoothContext?.deviceCategories?.contains(BluetoothCategory.HEADPHONES) == true) 1.0 else 0.0,
                hasBluetoothSpeakers = if (contextData.bluetoothContext?.deviceCategories?.contains(BluetoothCategory.SPEAKERS) == true) 1.0 else 0.0,
                hasBluetoothCar = if (contextData.bluetoothContext?.deviceCategories?.contains(BluetoothCategory.CAR) == true) 1.0 else 0.0,
                hasBluetoothKeyboard = if (contextData.bluetoothContext?.deviceCategories?.contains(BluetoothCategory.KEYBOARD) == true) 1.0 else 0.0,
                hasBluetoothMouse = if (contextData.bluetoothContext?.deviceCategories?.contains(BluetoothCategory.MOUSE) == true) 1.0 else 0.0,
                hasBluetoothWatch = if (contextData.bluetoothContext?.deviceCategories?.contains(BluetoothCategory.WATCH) == true) 1.0 else 0.0,
                hasBluetoothFitness = if (contextData.bluetoothContext?.deviceCategories?.contains(BluetoothCategory.FITNESS_TRACKER) == true) 1.0 else 0.0,
                bluetoothDeviceCount = (contextData.bluetoothContext?.connectedDevices?.size?.toDouble() ?: 0.0) / MAX_BLUETOOTH_DEVICES,
                
                // Device encoding
                isCharging = if (contextData.deviceContext?.isCharging == true) 1.0 else 0.0,
                isPortrait = if (contextData.deviceContext?.orientation == Orientation.PORTRAIT) 1.0 else 0.0
            )
        }
        
        /**
         * Normalize a string to a hash value between 0.0 and 1.0.
         */
        private fun normalizeStringHash(value: String?): Double {
            if (value == null) return 0.0
            // Use a simple hash function and normalize to 0.0-1.0
            val hash = value.hashCode().toLong()
            val positiveHash = if (hash < 0) -hash else hash
            return (positiveHash % 10000) / 10000.0
        }
    }
}