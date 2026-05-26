package com.spbalkan.pingme.ble

sealed class BlePermissionState {
    object Granted       : BlePermissionState()
    object Denied        : BlePermissionState()
    object NotDetermined : BlePermissionState()
    object Restricted    : BlePermissionState() // iOS only
}