package com.spbalkan.pingme.home.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spbalkan.pingme.ble.BluetoothManager
import com.spbalkan.pingme.customLoggerD
import com.spbalkan.pingme.models.BluetoothPeer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(private val bluetoothManager: BluetoothManager): ViewModel() {
    val _state = MutableStateFlow(HomeState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            bluetoothManager.discoveredPeers.collect { value ->
                _state.value = _state.value.copy(
                    devicesList = value
                )

                //                bluetoothManager.connectToPeer(value[0].id)
            }
        }
    }

    fun onAction(intent: ActionIntents) {
        when (intent) {
            is ActionIntents.OnStartScan -> {
                customLoggerD(TAG, "onAction: OnStartScan")
                bluetoothManager.startScanning()
            }

            is ActionIntents.OnStartAdvertising -> {
                customLoggerD(TAG, "onAction: OnStartAdvertising")
                bluetoothManager.startAdvertising("IpNitta")
            }

            is ActionIntents.OnOpenDeviceDetails -> {
                _state.value = _state.value.copy(
                    showDeviceDetails = true,
                    deviceDetails = intent.selectedPeer
                )
            }

            is ActionIntents.OnCloseDeviceDetails -> {
                _state.value = _state.value.copy(
                    showDeviceDetails = false,
                    deviceDetails = null
                )
            }
        }
    }

    data class HomeState(
        val devicesList: List<BluetoothPeer> = emptyList(),
        val showDeviceDetails: Boolean = false,
        val deviceDetails: BluetoothPeer? = null
    )

    sealed class ActionIntents {
        data object OnStartScan : ActionIntents()
        data object OnStartAdvertising : ActionIntents()
        data class OnOpenDeviceDetails(val selectedPeer: BluetoothPeer) : ActionIntents()
        data object OnCloseDeviceDetails : ActionIntents()
    }

    companion object {
        const val TAG = "z&zHomeVM"
    }
}