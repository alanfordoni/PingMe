package com.spbalkan.pingme.kotlin.di

import com.spbalkan.pingme.ble.BluetoothManager
import com.spbalkan.pingme.ble.IosBluetoothManager
import com.spbalkan.pingme.ble.IosBlePermissionHandler
import com.spbalkan.pingme.ble.BlePermissionHandler
import com.spbalkan.pingme.di.commonModule
import org.koin.core.context.startKoin
import org.koin.dsl.bind
import org.koin.dsl.module

val platformModule = module {
    single { IosBluetoothManager() } bind BluetoothManager::class

    single<BlePermissionHandler> { IosBlePermissionHandler() }
}

fun initIosKoin() {
    startKoin {
        modules(commonModule, platformModule)
    }
}