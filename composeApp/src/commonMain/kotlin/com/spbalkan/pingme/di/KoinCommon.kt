package com.spbalkan.pingme.di

import com.spbalkan.pingme.home.viewmodel.HomeViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val commonModule = module {
    // Koin automatically resolves BluetoothManager because it will be provided in the platform modules

    viewModelOf(::HomeViewModel)
}