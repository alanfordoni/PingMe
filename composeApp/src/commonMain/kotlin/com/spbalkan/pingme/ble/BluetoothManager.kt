package com.spbalkan.pingme.ble

import com.spbalkan.pingme.models.BluetoothPeer
import com.spbalkan.pingme.models.Message
import kotlinx.coroutines.flow.Flow

interface BluetoothManager {
    fun isEnabled(): Boolean
    fun startAdvertising(username: String)
    fun startScanning()
    fun stopAll()
    fun connectToPeer(peerId: String)
    fun sendMessage(message: String, peerId: String)
    val discoveredPeers: Flow<List<BluetoothPeer>>
    val connectionState: Flow<ConnectionState> // Track if we are connecting/connected
    val incomingMessages: Flow<Message>
}

sealed class ConnectionState {
    // The initial state when no connection is active
    object Idle : ConnectionState()
    // Actively attempting to establish a physical BLE link.
    data class Connecting(val deviceId: String) : ConnectionState()
    // Link established, but still performing GATT service discovery and characteristic subscription (crucial for BLE)
    data class DiscoveringServices(val deviceId: String) : ConnectionState()
    // Fully connected and ready to send/receive chat messages
    data class Connected(val peer: BluetoothPeer) : ConnectionState()
    // Connection was lost or intentionally closed
    data class Disconnected(val message: String? = null) : ConnectionState()
    // An error occurred (e.g., Bluetooth turned off, unauthorized, or timeout)
    data class Error(val throwable: Throwable) : ConnectionState()
}