package com.spbalkan.pingme.home.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spbalkan.pingme.ble.BlePermissionHandler
import com.spbalkan.pingme.ble.BlePermissionState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class BlePermissionViewModel(
    private val permissionHandler: BlePermissionHandler
) : ViewModel() {

    val permissionState: StateFlow<BlePermissionState> = permissionHandler.permissionState
    val bluetoothState  = permissionHandler.bluetoothState

    init {
        checkInitialState()
        permissionHandler.registerBluetoothStateMonitor()
    }

    override fun onCleared() {
        super.onCleared()
        permissionHandler.unregisterBluetoothStateMonitor()
    }

    private fun checkInitialState() {
        viewModelScope.launch {
            permissionHandler.checkInitialState()
        }
    }

    fun requestPermissions() {
        viewModelScope.launch {
            val result = permissionHandler.requestPermissions()
            if (result is BlePermissionState.Denied) {
                // ToDo show rationale or settings
            }
        }
    }

    fun openSettings() = permissionHandler.openAppSettings()

    fun openBluetoothSettings() {
        permissionHandler.openBluetoothSettings()
    }
}

