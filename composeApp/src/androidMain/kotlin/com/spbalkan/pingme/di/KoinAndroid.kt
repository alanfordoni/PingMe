package com.spbalkan.pingme.di

import com.spbalkan.pingme.ble.AndroidBluetoothManager
import com.spbalkan.pingme.ble.AndroidBlePermissionHandler
import com.spbalkan.pingme.ble.BluetoothManager
import com.spbalkan.pingme.ble.BlePermissionHandler
import org.koin.dsl.bind
import org.koin.dsl.module

val platformModule = module {
    single { AndroidBluetoothManager(get()) } bind BluetoothManager::class

    single<BlePermissionHandler> { AndroidBlePermissionHandler(get()) }
}