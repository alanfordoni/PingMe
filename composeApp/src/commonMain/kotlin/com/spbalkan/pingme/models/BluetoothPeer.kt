package com.spbalkan.pingme.models

data class BluetoothPeer(
    val id: String,                 // MAC Address (Android) or UUID String (iOS)
    val name: String,               // Broadcasted Device Name
    val rssi: Int,                  // Signal strength (Crucial for distance tracking)
    val lastSeen: Long,             // Timestamp for managing stale device cleanups
    val isConnectable: Boolean,     // True if the device supports GATT connections
    val nativeRef: Any? = null      // Holds BluetoothDevice (Android) or CBPeripheral (iOS)
)

