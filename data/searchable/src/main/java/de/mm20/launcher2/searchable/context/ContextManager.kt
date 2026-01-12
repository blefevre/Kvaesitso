package de.mm20.launcher2.searchable.context

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.BroadcastReceiver
import android.net.ConnectivityManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.os.BatteryManager
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * Manages collection of context data from multiple providers.
 * Coordinates the gathering of time, network, Bluetooth, and device context.
 */
class ContextManager(
    private val context: Context
) {
    private val providers = listOf(
        TimeContextProvider(),
        WifiContextProvider(context),
        BluetoothContextProvider(context),
        DeviceContextProvider(context)
    )
    
    /**
     * Flow that emits current context data on demand.
     * Initialized eagerly with current context to ensure immediate availability.
     */
    private val _contextFlow = MutableStateFlow<ContextData?>(null)
    val contextFlow: Flow<ContextData> = _contextFlow.filterNotNull()
    
    private var lastContext: ContextData? = null
    private var systemEventReceiver: BroadcastReceiver? = null
    private var isSystemMonitoringEnabled = false
    
    init {
        // Initialize with current context asynchronously
        kotlinx.coroutines.GlobalScope.launch {
            try {
                val initialContext = getCurrentContext()
                _contextFlow.value = initialContext
                lastContext = initialContext
                android.util.Log.d("ContextManager", "Initialized with context: $initialContext")
                
                // Start monitoring system events for automatic context updates
                startSystemEventMonitoring()
            } catch (e: Exception) {
                android.util.Log.e("ContextManager", "Failed to initialize context", e)
            }
        }
    }
    
    /**
     * Start monitoring system events that may indicate context changes.
     * Automatically refreshes context when relevant system events occur.
     */
    private fun startSystemEventMonitoring() {
        if (isSystemMonitoringEnabled) return
        
        try {
            systemEventReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    when (intent?.action) {
                        ConnectivityManager.CONNECTIVITY_ACTION -> {
                            android.util.Log.d("ContextManager", "Network connectivity changed")
                            kotlinx.coroutines.GlobalScope.launch {
                                refreshContext()
                            }
                        }
                        BluetoothAdapter.ACTION_STATE_CHANGED,
                        BluetoothDevice.ACTION_ACL_CONNECTED,
                        BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                            android.util.Log.d("ContextManager", "Bluetooth state changed: ${intent.action}")
                            kotlinx.coroutines.GlobalScope.launch {
                                refreshContext()
                            }
                        }
                        Intent.ACTION_POWER_CONNECTED,
                        Intent.ACTION_POWER_DISCONNECTED -> {
                            android.util.Log.d("ContextManager", "Power state changed: ${intent.action}")
                            kotlinx.coroutines.GlobalScope.launch {
                                refreshContext()
                            }
                        }
                        Intent.ACTION_CONFIGURATION_CHANGED -> {
                            android.util.Log.d("ContextManager", "Configuration changed (orientation, etc.)")
                            kotlinx.coroutines.GlobalScope.launch {
                                refreshContext()
                            }
                        }
                    }
                }
            }
            
            val intentFilter = IntentFilter().apply {
                // Network changes
                addAction(ConnectivityManager.CONNECTIVITY_ACTION)
                
                // Bluetooth changes
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                
                // Power/charging changes
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
                
                // Orientation/configuration changes
                addAction(Intent.ACTION_CONFIGURATION_CHANGED)
            }
            
            context.registerReceiver(systemEventReceiver, intentFilter)
            isSystemMonitoringEnabled = true
            android.util.Log.d("ContextManager", "System event monitoring started")
            
        } catch (e: Exception) {
            android.util.Log.e("ContextManager", "Failed to start system event monitoring", e)
        }
    }
    
    /**
     * Stop monitoring system events.
     * Should be called when ContextManager is no longer needed.
     */
    fun stopSystemEventMonitoring() {
        try {
            systemEventReceiver?.let { receiver ->
                context.unregisterReceiver(receiver)
                systemEventReceiver = null
                isSystemMonitoringEnabled = false
                android.util.Log.d("ContextManager", "System event monitoring stopped")
            }
        } catch (e: Exception) {
            android.util.Log.e("ContextManager", "Failed to stop system event monitoring", e)
        }
    }
    
    /**
     * Refresh the current context and emit it to listeners.
     * Only emits if context has meaningfully changed.
     */
    suspend fun refreshContext() {
        try {
            val newContext = getCurrentContext()
            
            // Check if context has meaningfully changed
            if (hasContextChanged(lastContext, newContext)) {
                val diff = getContextDiff(lastContext, newContext)
                android.util.Log.d("ContextManager", "Context changed: $diff")
                _contextFlow.value = newContext
                lastContext = newContext
            } else {
                android.util.Log.d("ContextManager", "Context unchanged, skipping emission")
            }
        } catch (e: Exception) {
            android.util.Log.e("ContextManager", "Failed to refresh context", e)
        }
    }
    
    /**
     * Check if context has meaningfully changed.
     * Returns true if any significant context dimension has changed.
     */
    private fun hasContextChanged(old: ContextData?, new: ContextData): Boolean {
        if (old == null) return true
        
        // Check time changes (hour or time slot changes)
        val timeChanged = old.timeContext?.hour != new.timeContext?.hour ||
                         old.timeContext?.timeSlot != new.timeContext?.timeSlot ||
                         old.timeContext?.dayOfWeek != new.timeContext?.dayOfWeek
        
        // Check network changes (WiFi SSID or connection type changes)  
        val networkChanged = old.networkContext?.wifiSsid != new.networkContext?.wifiSsid ||
                            old.networkContext?.connectionType != new.networkContext?.connectionType
        
        // Check Bluetooth changes (connected devices or categories changed)
        val bluetoothChanged = old.bluetoothContext?.connectedDevices != new.bluetoothContext?.connectedDevices ||
                              old.bluetoothContext?.deviceCategories != new.bluetoothContext?.deviceCategories
        
        // Check device changes (charging state or orientation changed)
        val deviceChanged = old.deviceContext?.isCharging != new.deviceContext?.isCharging ||
                           old.deviceContext?.orientation != new.deviceContext?.orientation
        
        val changed = timeChanged || networkChanged || bluetoothChanged || deviceChanged
        
        if (changed) {
            android.util.Log.d("ContextManager", "Context change detected - time:$timeChanged, network:$networkChanged, bluetooth:$bluetoothChanged, device:$deviceChanged")
        }
        
        return changed
    }

    /**
     * Collect current context from all available providers.
     * Runs providers in parallel for optimal performance.
     */
    suspend fun getCurrentContext(): ContextData = coroutineScope {
        val timeContextDeferred = async { 
            providers.find { it.providerId == "time" }?.getCurrentContext() as? TimeContext 
        }
        val networkContextDeferred = async { 
            providers.find { it.providerId == "wifi" }?.getCurrentContext() as? NetworkContext 
        }
        val bluetoothContextDeferred = async { 
            providers.find { it.providerId == "bluetooth" }?.getCurrentContext() as? BluetoothContext 
        }
        val deviceContextDeferred = async { 
            providers.find { it.providerId == "device" }?.getCurrentContext() as? DeviceContext 
        }

        ContextData(
            timestamp = System.currentTimeMillis(),
            timeContext = timeContextDeferred.await(),
            networkContext = networkContextDeferred.await(),
            bluetoothContext = bluetoothContextDeferred.await(),
            deviceContext = deviceContextDeferred.await()
        )
    }

    /**
     * Calculate a context-aware weight boost for the given historical context.
     * Returns a multiplier (1.0 = no boost, >1.0 = boost, <1.0 = penalty).
     * 
     * @param historicalContexts List of contexts when the app was previously used
     * @param currentContext Current context
     * @param baseWeight Base weight from traditional frequency calculation
     */
    fun calculateContextAwareWeight(
        historicalContexts: List<ContextData>,
        currentContext: ContextData,
        baseWeight: Double
    ): Double {
        if (historicalContexts.isEmpty()) return baseWeight

        // Find the best matching historical context
        val maxSimilarity = historicalContexts.maxOfOrNull { context ->
            currentContext.similarityScore(context)
        } ?: 0.0

        // Apply context boost based on similarity
        val contextMultiplier = when {
            maxSimilarity >= 0.8 -> 1.5  // High similarity - significant boost
            maxSimilarity >= 0.6 -> 1.3  // Good similarity - moderate boost  
            maxSimilarity >= 0.4 -> 1.1  // Some similarity - small boost
            maxSimilarity >= 0.2 -> 0.9  // Low similarity - small penalty
            else -> 0.7                  // No similarity - larger penalty
        }

        return baseWeight * contextMultiplier
    }

    /**
     * Analyze usage patterns to identify strong contextual associations.
     * Returns contexts where this app is frequently used.
     */
    fun analyzeUsagePatterns(historicalContexts: List<ContextData>): Set<String> {
        val patterns = mutableSetOf<String>()
        
        if (historicalContexts.size < 3) return patterns

        // Analyze time patterns
        val timeHours = historicalContexts.mapNotNull { it.timeContext?.hour }
        val mostCommonHour = timeHours.groupBy { it }.maxByOrNull { it.value.size }?.key
        if (mostCommonHour != null && timeHours.count { it == mostCommonHour } >= timeHours.size * 0.4) {
            patterns.add("time_${mostCommonHour}h")
        }

        // Analyze day of week patterns
        val dayOfWeeks = historicalContexts.mapNotNull { it.timeContext?.dayOfWeek }
        val mostCommonDay = dayOfWeeks.groupBy { it }.maxByOrNull { it.value.size }?.key
        if (mostCommonDay != null && dayOfWeeks.count { it == mostCommonDay } >= dayOfWeeks.size * 0.5) {
            patterns.add("day_$mostCommonDay")
        }

        // Analyze WiFi patterns
        val wifiNetworks = historicalContexts.mapNotNull { it.networkContext?.wifiSsid }
        val mostCommonWifi = wifiNetworks.groupBy { it }.maxByOrNull { it.value.size }?.key
        if (mostCommonWifi != null && wifiNetworks.count { it == mostCommonWifi } >= wifiNetworks.size * 0.6) {
            patterns.add("wifi_$mostCommonWifi")
        }

        // Analyze Bluetooth patterns
        historicalContexts.forEach { context ->
            context.bluetoothContext?.deviceCategories?.forEach { category ->
                val categoryCount = historicalContexts.count { 
                    it.bluetoothContext?.deviceCategories?.contains(category) == true 
                }
                if (categoryCount >= historicalContexts.size * 0.4) {
                    patterns.add("bluetooth_${category.name.lowercase()}")
                }
            }
        }

        return patterns
    }
    
    /**
     * Get debugging information about context changes.
     * Returns a detailed comparison between old and new context.
     */
    fun getContextDiff(old: ContextData?, new: ContextData): Map<String, Any> {
        val diff = mutableMapOf<String, Any>()
        
        if (old == null) {
            diff["status"] = "Initial context"
            diff["context"] = new
            return diff
        }
        
        // Time changes
        if (old.timeContext?.hour != new.timeContext?.hour) {
            diff["time.hour"] = "${old.timeContext?.hour} → ${new.timeContext?.hour}"
        }
        if (old.timeContext?.timeSlot != new.timeContext?.timeSlot) {
            diff["time.slot"] = "${old.timeContext?.timeSlot} → ${new.timeContext?.timeSlot}"
        }
        if (old.timeContext?.dayOfWeek != new.timeContext?.dayOfWeek) {
            diff["time.dayOfWeek"] = "${old.timeContext?.dayOfWeek} → ${new.timeContext?.dayOfWeek}"
        }
        
        // Network changes
        if (old.networkContext?.wifiSsid != new.networkContext?.wifiSsid) {
            diff["network.wifi"] = "${old.networkContext?.wifiSsid} → ${new.networkContext?.wifiSsid}"
        }
        if (old.networkContext?.connectionType != new.networkContext?.connectionType) {
            diff["network.type"] = "${old.networkContext?.connectionType} → ${new.networkContext?.connectionType}"
        }
        
        // Bluetooth changes
        val oldDevices = old.bluetoothContext?.connectedDevices ?: emptySet()
        val newDevices = new.bluetoothContext?.connectedDevices ?: emptySet()
        if (oldDevices != newDevices) {
            val added = newDevices - oldDevices
            val removed = oldDevices - newDevices
            if (added.isNotEmpty()) diff["bluetooth.added"] = added
            if (removed.isNotEmpty()) diff["bluetooth.removed"] = removed
        }
        
        val oldCategories = old.bluetoothContext?.deviceCategories ?: emptySet()
        val newCategories = new.bluetoothContext?.deviceCategories ?: emptySet()
        if (oldCategories != newCategories) {
            diff["bluetooth.categories"] = "$oldCategories → $newCategories"
        }
        
        // Device changes
        if (old.deviceContext?.isCharging != new.deviceContext?.isCharging) {
            diff["device.charging"] = "${old.deviceContext?.isCharging} → ${new.deviceContext?.isCharging}"
        }
        if (old.deviceContext?.orientation != new.deviceContext?.orientation) {
            diff["device.orientation"] = "${old.deviceContext?.orientation} → ${new.deviceContext?.orientation}"
        }
        
        if (diff.isEmpty()) {
            diff["status"] = "No meaningful changes detected"
        }
        
        return diff
    }
    
    /**
     * Get current context flow statistics for debugging.
     */
    fun getContextFlowStats(): Map<String, Any> {
        return mapOf(
            "lastContext" to (lastContext?.toString() ?: "null"),
            "currentFlowValue" to (_contextFlow.value?.toString() ?: "null"),
            "systemMonitoringEnabled" to isSystemMonitoringEnabled,
            "timestamp" to System.currentTimeMillis()
        )
    }
}