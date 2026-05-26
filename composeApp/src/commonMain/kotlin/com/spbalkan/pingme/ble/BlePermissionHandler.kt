package com.spbalkan.pingme.ble

import kotlinx.coroutines.flow.StateFlow

interface BlePermissionHandler {
    val permissionState: StateFlow<BlePermissionState>
    val bluetoothState: StateFlow<Boolean>
    fun registerBluetoothStateMonitor()
    fun unregisterBluetoothStateMonitor()
    fun checkInitialState()
    suspend fun requestPermissions(): BlePermissionState
    fun openAppSettings()
    fun openBluetoothSettings()

}



