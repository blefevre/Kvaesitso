package de.mm20.launcher2.ui.settings.favorites

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Helper for requesting Bluetooth permissions in the debug interface.
 */
@Composable
fun BluetoothPermissionHandler(
    onPermissionGranted: () -> Unit,
    onPermissionDenied: () -> Unit,
    content: @Composable (requestPermission: () -> Unit, hasPermission: Boolean) -> Unit
) {
    val context = LocalContext.current
    var showPermissionDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    
    val requiredPermissions = getRequiredBluetoothPermissions()
    val hasPermission = checkBluetoothPermissions(context)
    
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            onPermissionGranted()
        } else {
            // Check if user denied permanently
            val permanentlyDenied = permissions.any { (permission, granted) ->
                !granted && !shouldShowRationale(context, permission)
            }
            
            if (permanentlyDenied) {
                showSettingsDialog = true
            } else {
                onPermissionDenied()
            }
        }
    }
    
    val requestPermission = {
        when {
            hasPermission -> onPermissionGranted()
            shouldShowPermissionRationale(context, requiredPermissions) -> showPermissionDialog = true
            else -> permissionLauncher.launch(requiredPermissions.toTypedArray())
        }
    }
    
    content(requestPermission, hasPermission)
    
    // Permission rationale dialog
    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("Bluetooth Permission Required") },
            text = { 
                Text(
                    "Smart Favorites needs Bluetooth permission to detect connected devices like headphones, " +
                    "speakers, and car systems. This helps suggest apps based on your Bluetooth context.\n\n" +
                    "For example: automatically suggesting music apps when headphones are connected."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionDialog = false
                        permissionLauncher.launch(requiredPermissions.toTypedArray())
                    }
                ) {
                    Text("Grant Permission")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPermissionDialog = false
                        onPermissionDenied()
                    }
                ) {
                    Text("Not Now")
                }
            }
        )
    }
    
    // Settings dialog for permanently denied permissions
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("Permission Required") },
            text = { 
                Text(
                    "Bluetooth permission was permanently denied. To enable Smart Favorites " +
                    "Bluetooth detection, please grant the permission in Settings."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSettingsDialog = false
                        openAppSettings(context)
                    }
                ) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showSettingsDialog = false
                        onPermissionDenied()
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun getRequiredBluetoothPermissions(): List<String> {
    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN
        )
    } else {
        listOf(Manifest.permission.BLUETOOTH)
    }
    android.util.Log.d("BluetoothPermissionHelper", "Android SDK: ${Build.VERSION.SDK_INT}, Required permissions: $permissions")
    return permissions
}

private fun checkBluetoothPermissions(context: Context): Boolean {
    val requiredPermissions = getRequiredBluetoothPermissions()
    val result = requiredPermissions.all { permission ->
        val granted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        android.util.Log.d("BluetoothPermissionHelper", "Permission $permission: $granted")
        granted
    }
    android.util.Log.d("BluetoothPermissionHelper", "Overall Bluetooth permissions granted: $result")
    return result
}

private fun shouldShowPermissionRationale(context: Context, permissions: List<String>): Boolean {
    // This is a simplified check - in a real Activity, you'd use ActivityCompat.shouldShowRequestPermissionRationale
    // For Compose, we'll show the rationale dialog on first request
    return false
}

private fun shouldShowRationale(context: Context, permission: String): Boolean {
    // This would normally check if we should show rationale, but in Compose context
    // we'll assume false for permanently denied check
    return false
}

/**
 * Helper for requesting Location permissions needed for WiFi SSID access.
 */
@Composable
fun LocationPermissionHandler(
    onPermissionGranted: () -> Unit,
    onPermissionDenied: () -> Unit,
    content: @Composable (requestPermission: () -> Unit, hasPermission: Boolean) -> Unit
) {
    val context = LocalContext.current
    var showPermissionDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    
    val requiredPermissions = listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    val hasPermission = checkLocationPermissions(context)
    
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            onPermissionGranted()
        } else {
            val permanentlyDenied = permissions.any { (permission, granted) ->
                !granted && !shouldShowRationale(context, permission)
            }
            
            if (permanentlyDenied) {
                showSettingsDialog = true
            } else {
                onPermissionDenied()
            }
        }
    }
    
    val requestPermission = {
        when {
            hasPermission -> onPermissionGranted()
            else -> {
                showPermissionDialog = true
            }
        }
    }
    
    content(requestPermission, hasPermission)
    
    // Permission rationale dialog
    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("Location Permission Required") },
            text = { 
                Text(
                    "Smart Favorites needs location permission to detect which WiFi network you're connected to. " +
                    "This helps suggest apps based on your location context.\n\n" +
                    "For example: automatically suggesting work apps when connected to office WiFi, " +
                    "or entertainment apps when connected to home WiFi."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionDialog = false
                        permissionLauncher.launch(requiredPermissions.toTypedArray())
                    }
                ) {
                    Text("Grant Permission")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPermissionDialog = false
                        onPermissionDenied()
                    }
                ) {
                    Text("Not Now")
                }
            }
        )
    }
    
    // Settings dialog
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("Permission Required") },
            text = { 
                Text(
                    "Location permission was permanently denied. To enable WiFi network detection " +
                    "for Smart Favorites, please grant the permission in Settings."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSettingsDialog = false
                        openAppSettings(context)
                    }
                ) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showSettingsDialog = false
                        onPermissionDenied()
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun checkLocationPermissions(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context, 
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
}

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
    }
    context.startActivity(intent)
}

/**
 * Comprehensive permission handler for Smart Favorites that automatically requests
 * all necessary permissions (Bluetooth and Location).
 */
@Composable
fun SmartFavoritesPermissionHandler(
    enabled: Boolean,
    onAllPermissionsGranted: () -> Unit,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    var permissionsToRequest by remember { mutableStateOf<List<String>>(emptyList()) }
    var showRationaleDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    
    // Get all required permissions
    val bluetoothPermissions = getRequiredBluetoothPermissions()
    val locationPermissions = listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    val allRequiredPermissions = bluetoothPermissions + locationPermissions
    
    // Check current permission status
    val hasBluetoothPermission = checkBluetoothPermissions(context)
    val hasLocationPermission = checkLocationPermissions(context)
    val hasAllPermissions = hasBluetoothPermission && hasLocationPermission
    
    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            onAllPermissionsGranted()
        } else {
            // Check if any were permanently denied
            val permanentlyDenied = permissions.any { (permission, granted) ->
                !granted && !shouldShowRationale(context, permission)
            }
            
            if (permanentlyDenied) {
                showSettingsDialog = true
            }
        }
    }
    
    // Automatically request permissions when enabled and permissions are missing
    LaunchedEffect(enabled, hasAllPermissions) {
        if (enabled && !hasAllPermissions) {
            val missingPermissions = mutableListOf<String>()
            if (!hasBluetoothPermission) missingPermissions.addAll(bluetoothPermissions)
            if (!hasLocationPermission) missingPermissions.addAll(locationPermissions)
            
            permissionsToRequest = missingPermissions
            
            // Show rationale first, then request
            showRationaleDialog = true
        }
    }
    
    content()
    
    // Rationale dialog
    if (showRationaleDialog && permissionsToRequest.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { 
                showRationaleDialog = false
                // If user dismisses without granting, disable smart favorites
            },
            title = { Text("Permissions Required for Smart Favorites") },
            text = { 
                Text(
                    buildString {
                        append("Smart Favorites needs the following permissions to work properly:\n\n")
                        
                        if (!hasLocationPermission) {
                            append("📍 Location Permission\n")
                            append("• Detect which WiFi network you're connected to\n")
                            append("• Suggest apps based on your location (home, work, etc.)\n\n")
                        }
                        
                        if (!hasBluetoothPermission) {
                            append("🎧 Bluetooth Permission\n")
                            append("• Detect connected devices (headphones, car, speakers)\n")
                            append("• Suggest apps based on your connected devices\n\n")
                        }
                        
                        append("These permissions help Smart Favorites learn your app usage patterns and make better suggestions.")
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRationaleDialog = false
                        permissionLauncher.launch(permissionsToRequest.toTypedArray())
                    }
                ) {
                    Text("Grant Permissions")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showRationaleDialog = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Settings redirect dialog
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("Permissions Required") },
            text = { 
                Text(
                    "Some permissions were permanently denied. To use Smart Favorites with full functionality, " +
                    "please grant these permissions in Settings."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSettingsDialog = false
                        openAppSettings(context)
                    }
                ) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSettingsDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}