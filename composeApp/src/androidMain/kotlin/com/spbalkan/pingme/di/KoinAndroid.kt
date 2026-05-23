package com.spbalkan.pingme.di

import com.spbalkan.pingme.ble.AndroidBluetoothManager
import com.spbalkan.pingme.ble.BluetoothManager
import org.koin.dsl.bind
import org.koin.dsl.module

val platformModule = module {
    single { AndroidBluetoothManager(get()) } bind BluetoothManager::class
}