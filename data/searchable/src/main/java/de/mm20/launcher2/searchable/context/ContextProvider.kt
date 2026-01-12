package de.mm20.launcher2.searchable.context

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Interface for providing context information for smart favorites.
 * Implementations should gather specific types of context data.
 */
interface ContextProvider {
    /**
     * Collect current context information.
     * Should be lightweight and fast as it's called on every app launch.
     */
    suspend fun getCurrentContext(): Any?

    /**
     * Get a unique identifier for this provider type.
     */
    val providerId: String
}

/**
 * Provides time-based context information.
 */
class TimeContextProvider : ContextProvider {
    override val providerId: String = "time"

    override suspend fun getCurrentContext(): TimeContext {
        val now = java.time.LocalDateTime.now()
        val hour = now.hour
        val dayOfWeek = now.dayOfWeek.value
        val timeSlot = when (hour) {
            in 5..8 -> TimeSlot.EARLY_MORNING
            in 9..11 -> TimeSlot.MORNING
            in 12..14 -> TimeSlot.MIDDAY
            in 15..17 -> TimeSlot.AFTERNOON
            in 18..20 -> TimeSlot.EVENING
            in 21..23 -> TimeSlot.NIGHT
            else -> TimeSlot.LATE_NIGHT
        }
        
        return TimeContext(hour = hour, dayOfWeek = dayOfWeek, timeSlot = timeSlot)
    }
}

/**
 * Provides WiFi network context information.
 */
class WifiContextProvider(private val context: Context) : ContextProvider {
    override val providerId: String = "wifi"

    override suspend fun getCurrentContext(): NetworkContext {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) 
                as android.net.ConnectivityManager
            val activeNetwork = connectivityManager.activeNetwork
            val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)

            Log.d("WifiContext", "Active network: $activeNetwork")
            Log.d("WifiContext", "Network capabilities: $networkCapabilities")

            when {
                networkCapabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true -> {
                    val wifiSsid = getWifiSsid()
                    Log.d("WifiContext", "WiFi detected, SSID: $wifiSsid")
                    NetworkContext(
                        wifiSsid = wifiSsid,
                        isConnected = true,
                        connectionType = ConnectionType.WIFI
                    )
                }
                networkCapabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) == true -> {
                    Log.d("WifiContext", "Mobile data detected")
                    NetworkContext(
                        wifiSsid = null,
                        isConnected = true,
                        connectionType = ConnectionType.MOBILE
                    )
                }
                else -> {
                    Log.d("WifiContext", "No connection detected")
                    NetworkContext(
                        wifiSsid = null,
                        isConnected = false,
                        connectionType = ConnectionType.NONE
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("WifiContext", "Error getting network context", e)
            // Fallback to no connection if we can't determine network state
            NetworkContext(
                wifiSsid = null,
                isConnected = false,
                connectionType = ConnectionType.NONE
            )
        }
    }

    private fun getWifiSsid(): String? {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) 
                as android.net.wifi.WifiManager
            val wifiInfo = wifiManager.connectionInfo
            
            val rawSsid = wifiInfo.ssid
            Log.d("WifiContext", "Raw SSID from WifiManager: $rawSsid")
            
            val ssid = rawSsid?.removeSurrounding("\"")
            Log.d("WifiContext", "Cleaned SSID: $ssid")
            
            // Android 10+ may return "<unknown ssid>" when location permission is missing
            // or when the app is targeting API 29+
            if (ssid != null && ssid != "<unknown ssid>" && ssid != "0x" && ssid.isNotBlank()) {
                Log.d("WifiContext", "Using valid SSID: $ssid")
                ssid
            } else {
                Log.d("WifiContext", "SSID not available, trying alternative detection")
                // Try alternative method using NetworkCallback (requires API 24+)
                tryAlternativeWifiDetection() ?: "Unknown WiFi"
            }
        } catch (e: Exception) {
            Log.e("WifiContext", "Error getting WiFi SSID", e)
            null
        }
    }
    
    private fun tryAlternativeWifiDetection(): String? {
        return try {
            // For newer Android versions, we can at least detect that we're on WiFi
            // even if we can't get the exact SSID due to privacy restrictions
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) 
                as android.net.ConnectivityManager
            val activeNetwork = connectivityManager.activeNetwork
            val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            
            if (networkCapabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true) {
                // We know it's WiFi but can't get SSID due to permissions
                "WiFi Network"
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * Provides Bluetooth context information.
 */
class BluetoothContextProvider(private val context: Context) : ContextProvider {
    override val providerId: String = "bluetooth"
    
    /**
     * Check if Bluetooth permissions are granted.
     */
    fun hasPermissions(): Boolean = hasBluetoothPermissions()
    
    /**
     * Get the required permissions for this Android version.
     */
    fun getRequiredPermissions(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                android.Manifest.permission.BLUETOOTH_CONNECT,
                android.Manifest.permission.BLUETOOTH_SCAN
            )
        } else {
            listOf(android.Manifest.permission.BLUETOOTH)
        }
    }

    override suspend fun getCurrentContext(): BluetoothContext {
        return try {
            // Check for required permissions first
            if (!hasBluetoothPermissions()) {
                Log.d("BluetoothContext", "Missing Bluetooth permissions, returning empty context")
                return BluetoothContext()
            }

            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) 
                as android.bluetooth.BluetoothManager
            val bluetoothAdapter = bluetoothManager.adapter

            Log.d("BluetoothContext", "Bluetooth adapter enabled: ${bluetoothAdapter?.isEnabled}")

            if (bluetoothAdapter?.isEnabled != true) {
                Log.d("BluetoothContext", "Bluetooth not enabled, returning empty context")
                return BluetoothContext()
            }

            val connectedDevices = mutableSetOf<String>()
            val deviceCategories = mutableSetOf<BluetoothCategory>()

            try {
                // For Android 12+, we need BLUETOOTH_CONNECT permission
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) 
                        != PackageManager.PERMISSION_GRANTED) {
                    Log.d("BluetoothContext", "BLUETOOTH_CONNECT permission not granted for Android 12+")
                    return BluetoothContext()
                }
                
                // Get bonded devices (paired devices)
                val bondedDevices = bluetoothAdapter.bondedDevices
                Log.d("BluetoothContext", "Bonded devices: ${bondedDevices?.size ?: 0}")
                
                // For testing purposes, let's first list all bonded devices
                bondedDevices?.forEach { device ->
                    Log.d("BluetoothContext", "Bonded device: ${device.name} (${device.address})")
                }
                
                // Use a simpler approach: check connected devices via profiles synchronously
                val connectedDevicesFound = getConnectedBluetoothDevices(bluetoothAdapter)
                
                connectedDevicesFound.forEach { device ->
                    val deviceName = device.name ?: device.address ?: "Unknown Device"
                    connectedDevices.add(deviceName)
                    
                    val category = categorizeBluetoothDevice(device)
                    deviceCategories.add(category)
                    
                    Log.d("BluetoothContext", "Connected device: $deviceName, category: $category")
                }

            } catch (e: SecurityException) {
                Log.e("BluetoothContext", "Security exception accessing Bluetooth devices", e)
                // Handle missing Bluetooth permissions gracefully
            } catch (e: Exception) {
                Log.e("BluetoothContext", "Error getting Bluetooth devices", e)
            }

            Log.d("BluetoothContext", "Final connected devices: ${connectedDevices.size}, categories: $deviceCategories")

            BluetoothContext(
                connectedDevices = connectedDevices,
                deviceCategories = deviceCategories
            )
        } catch (e: Exception) {
            Log.e("BluetoothContext", "Error getting Bluetooth context", e)
            BluetoothContext()
        }
    }

    private fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ requires BLUETOOTH_CONNECT
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            // Pre-Android 12 requires BLUETOOTH
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun getConnectedBluetoothDevices(bluetoothAdapter: android.bluetooth.BluetoothAdapter): List<android.bluetooth.BluetoothDevice> {
        val connectedDevices = mutableListOf<android.bluetooth.BluetoothDevice>()
        
        try {
            Log.d("BluetoothContext", "Starting Bluetooth device connection check")
            
            // Method 1: Check connection state directly for each bonded device
            bluetoothAdapter.bondedDevices?.forEach { device ->
                try {
                    // Try multiple reflection methods to check connection state
                    val deviceName = device.name ?: device.address
                    
                    // Method 1a: Try isConnected method
                    try {
                        val isConnectedMethod = device.javaClass.getMethod("isConnected")
                        val isConnected = isConnectedMethod.invoke(device) as? Boolean ?: false
                        Log.d("BluetoothContext", "Device $deviceName: isConnected() = $isConnected")
                        if (isConnected) {
                            connectedDevices.add(device)
                            return@forEach
                        }
                    } catch (e: Exception) {
                        Log.d("BluetoothContext", "isConnected() failed for $deviceName: ${e.message}")
                    }
                    
                    // Method 1b: Try getConnectionState method  
                    try {
                        val getConnectionStateMethod = device.javaClass.getMethod("getConnectionState")
                        val connectionState = getConnectionStateMethod.invoke(device) as? Int ?: 0
                        Log.d("BluetoothContext", "Device $deviceName: getConnectionState() = $connectionState")
                        if (connectionState == 2) { // STATE_CONNECTED = 2
                            connectedDevices.add(device)
                            return@forEach
                        }
                    } catch (e: Exception) {
                        Log.d("BluetoothContext", "getConnectionState() failed for $deviceName: ${e.message}")
                    }
                    
                    // Log bond state for debugging but don't use it for connection detection
                    try {
                        val bondState = device.bondState
                        Log.d("BluetoothContext", "Device $deviceName: bondState = $bondState (not using for connection)")
                    } catch (e: Exception) {
                        Log.d("BluetoothContext", "Bond state check failed for $deviceName: ${e.message}")
                    }
                    
                } catch (e: Exception) {
                    Log.e("BluetoothContext", "Error checking device ${device.name}: ${e.message}")
                }
            }
            
            // Remove duplicates
            val uniqueDevices = connectedDevices.distinctBy { it.address }
            connectedDevices.clear()
            connectedDevices.addAll(uniqueDevices)
            
        } catch (e: Exception) {
            Log.e("BluetoothContext", "Error getting connected devices", e)
        }
        
        Log.d("BluetoothContext", "Final result: ${connectedDevices.size} connected devices")
        connectedDevices.forEach { device ->
            Log.d("BluetoothContext", "Connected: ${device.name ?: device.address}")
        }
        
        return connectedDevices
    }

    private fun categorizeBluetoothDevice(device: android.bluetooth.BluetoothDevice): BluetoothCategory {
        return try {
            val deviceClass = device.bluetoothClass?.majorDeviceClass
            when (deviceClass) {
                android.bluetooth.BluetoothClass.Device.Major.AUDIO_VIDEO -> {
                    when {
                        device.name?.contains("headphone", ignoreCase = true) == true -> BluetoothCategory.HEADPHONES
                        device.name?.contains("speaker", ignoreCase = true) == true -> BluetoothCategory.SPEAKERS
                        device.name?.contains("car", ignoreCase = true) == true -> BluetoothCategory.CAR
                        else -> BluetoothCategory.HEADPHONES
                    }
                }
                android.bluetooth.BluetoothClass.Device.Major.PERIPHERAL -> BluetoothCategory.KEYBOARD
                android.bluetooth.BluetoothClass.Device.Major.WEARABLE -> BluetoothCategory.WATCH
                else -> BluetoothCategory.OTHER
            }
        } catch (e: Exception) {
            BluetoothCategory.OTHER
        }
    }
}

/**
 * Provides device state context information.
 */
class DeviceContextProvider(private val context: Context) : ContextProvider {
    override val providerId: String = "device"

    override suspend fun getCurrentContext(): DeviceContext {
        val isCharging = getChargingStatus()
        val orientation = getOrientation()

        return DeviceContext(
            isCharging = isCharging,
            orientation = orientation
        )
    }


    private fun getChargingStatus(): Boolean {
        return try {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) 
                as android.os.BatteryManager
            val status = batteryManager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_STATUS)
            status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
            status == android.os.BatteryManager.BATTERY_STATUS_FULL
        } catch (e: Exception) {
            false
        }
    }

    private fun getOrientation(): Orientation {
        return try {
            val configuration = context.resources.configuration
            when (configuration.orientation) {
                android.content.res.Configuration.ORIENTATION_LANDSCAPE -> Orientation.LANDSCAPE
                else -> Orientation.PORTRAIT
            }
        } catch (e: Exception) {
            Orientation.PORTRAIT
        }
    }
}