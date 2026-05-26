package com.spbalkan.pingme.ble

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBCentralManagerDelegateProtocol
import platform.CoreBluetooth.CBManagerAuthorizationAllowedAlways
import platform.CoreBluetooth.CBManagerAuthorizationDenied
import platform.CoreBluetooth.CBManagerAuthorizationNotDetermined
import platform.CoreBluetooth.CBManagerAuthorizationRestricted
import platform.CoreBluetooth.CBManagerStatePoweredOn
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.darwin.NSObject

class IosBlePermissionHandler : BlePermissionHandler {

    private val _permissionState = MutableStateFlow(currentState())
    override val permissionState: StateFlow<BlePermissionState> = _permissionState
    private val _bluetoothState = MutableStateFlow(false)
    override val bluetoothState: StateFlow<Boolean> = _bluetoothState

    private val delegate = object : NSObject(), CBCentralManagerDelegateProtocol {
        override fun centralManagerDidUpdateState(central: CBCentralManager) {
            _bluetoothState.value = central.state == CBManagerStatePoweredOn
            _permissionState.value = currentState()
        }
    }

    override fun registerBluetoothStateMonitor() {
        if (centralManager == null) {
            centralManager = CBCentralManager(delegate, null)
        }
    }

    override fun unregisterBluetoothStateMonitor() {
        centralManager = null
    }

    override fun checkInitialState() {
        _permissionState.value = currentState()
        _bluetoothState.value = centralManager?.state == CBManagerStatePoweredOn
    }

    private var centralManager: CBCentralManager? = null

    override suspend fun requestPermissions(): BlePermissionState {
        if (centralManager == null) {
            centralManager = CBCentralManager(null, null)
        }
        return currentState().also { _permissionState.value = it }
    }

    override fun openAppSettings() {
        NSURL.URLWithString(UIApplicationOpenSettingsURLString)?.let {
            UIApplication.sharedApplication.openURL(it, emptyMap<Any?, Any>(), null)
        }
    }

    override fun openBluetoothSettings() {
        // todo check what is optimal for iOS app
    }

    private fun currentState(): BlePermissionState =
        when (CBCentralManager.authorization) {
            CBManagerAuthorizationAllowedAlways -> BlePermissionState.Granted
            CBManagerAuthorizationDenied        -> BlePermissionState.Denied
            CBManagerAuthorizationRestricted    -> BlePermissionState.Restricted
            CBManagerAuthorizationNotDetermined -> BlePermissionState.NotDetermined
            else                                -> BlePermissionState.NotDetermined
        }
}