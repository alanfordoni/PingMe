package com.spbalkan.pingme.ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class AndroidBlePermissionHandler(
    private val activity: ComponentActivity
) : BlePermissionHandler {

    private val blePermissions = arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.BLUETOOTH_CONNECT
    )

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                _bluetoothState.value = state == BluetoothAdapter.STATE_ON
            }
        }
    }

    private val _permissionState = MutableStateFlow(currentState())
    override val permissionState: StateFlow<BlePermissionState> = _permissionState

    private val _bluetoothState = MutableStateFlow(false)
    override val bluetoothState: StateFlow<Boolean> = _bluetoothState

    override fun registerBluetoothStateMonitor() {
        activity.registerReceiver(receiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
    }

    override fun unregisterBluetoothStateMonitor() {
        activity.unregisterReceiver(receiver)
    }

    override fun checkInitialState() {
        _permissionState.value = currentState()
        _bluetoothState.value = isBluetoothON()
    }

    override suspend fun requestPermissions(): BlePermissionState = suspendCancellableCoroutine { continuation ->
        // ToDo move outside of fun and initialise it once at init
        val launcher = activity.activityResultRegistry.register(
            "ble_permissions",
            ActivityResultContracts.RequestMultiplePermissions()
        ) { results ->
            val state = if (results.values.all { it }) BlePermissionState.Granted
            else BlePermissionState.Denied
            _permissionState.value = state
            continuation.resume(state)
        }
        launcher.launch(blePermissions)

        continuation.invokeOnCancellation {
            launcher.unregister()
            }
    }

    override fun openAppSettings() {
        activity.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", activity.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    override fun openBluetoothSettings() {
        activity.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private fun currentState(): BlePermissionState {
        val allGranted = blePermissions.all {
            ContextCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED
        }
        return if (allGranted) BlePermissionState.Granted else BlePermissionState.NotDetermined
    }

    private fun isBluetoothON(): Boolean {
        val bluetoothAdapter: BluetoothAdapter = (activity.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager).adapter
        return bluetoothAdapter.isEnabled
    }
}