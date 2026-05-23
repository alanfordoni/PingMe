package com.spbalkan.pingme.ble

import com.spbalkan.pingme.models.BluetoothPeer
import com.spbalkan.pingme.models.Message
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.CoreBluetooth.CBATTErrorRequestNotSupported
import platform.CoreBluetooth.CBATTErrorSuccess
import platform.CoreBluetooth.CBATTRequest
import platform.CoreBluetooth.CBAdvertisementDataLocalNameKey
import platform.CoreBluetooth.CBAdvertisementDataServiceUUIDsKey
import platform.CoreBluetooth.CBAttributePermissionsWriteable
import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBCentralManagerDelegateProtocol
import platform.CoreBluetooth.CBCentralManagerStatePoweredOn
import platform.CoreBluetooth.CBCharacteristic
import platform.CoreBluetooth.CBCharacteristicPropertyNotify
import platform.CoreBluetooth.CBCharacteristicPropertyWrite
import platform.CoreBluetooth.CBMutableCharacteristic
import platform.CoreBluetooth.CBMutableService
import platform.CoreBluetooth.CBPeripheral
import platform.CoreBluetooth.CBPeripheralDelegateProtocol
import platform.CoreBluetooth.CBPeripheralManager
import platform.CoreBluetooth.CBPeripheralManagerDelegateProtocol
import platform.CoreBluetooth.CBService
import platform.CoreBluetooth.CBUUID
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSLog
import platform.Foundation.create
import platform.Foundation.dataWithBytes
import platform.Foundation.timeIntervalSince1970
import platform.darwin.NSObject

class IosBluetoothManager : BluetoothManager {

    // UUIDs app identification
    private val SERVICE_UUID = CBUUID.UUIDWithString("6eb67544-cf6a-4fd9-b951-69a47342f741")
    private val MESSAGE_CHAR_UUID = CBUUID.UUIDWithString("12345678-1234-5678-1234-567812345678")

    // CoreBluetooth Components (Central for scanning/connecting, Peripheral for hosting GATT server)
    private val centralDelegate = CentralDelegate()
    private val peripheralDelegate = PeripheralDelegate()
    private var centralManager: CBCentralManager? = null
    private var peripheralManager: CBPeripheralManager? = null

    // Track active connected peripherals to write data back to them (sendMessage)
    private val connectedPeripherals = mutableMapOf<String, CBPeripheral>()
    private var writableCharacteristic: CBCharacteristic? = null
    private var localCharacteristic: CBMutableCharacteristic? = null

    // Match StateFlow/SharedFlow architecture
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected())
    override val connectionState = _connectionState.asStateFlow()

    private val _discoveredPeers = MutableStateFlow<List<BluetoothPeer>>(emptyList())
    override val discoveredPeers = _discoveredPeers.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<Message>()
    override val incomingMessages = _incomingMessages.asSharedFlow()

    init {
        centralManager = CBCentralManager(delegate = centralDelegate, null)
        peripheralManager = CBPeripheralManager(delegate = peripheralDelegate, null)
    }

    override fun isEnabled(): Boolean {
        return centralManager?.state == CBCentralManagerStatePoweredOn
    }

    override fun startScanning() {
        if (centralManager?.state == CBCentralManagerStatePoweredOn) {
            // Clear old scan items
            _discoveredPeers.value = emptyList()
            // Only scan for devices advertising app SERVICE_UUID
            centralManager?.scanForPeripheralsWithServices(
                serviceUUIDs = listOf(SERVICE_UUID),
                options = null
            )
        }
    }

    override fun startAdvertising(username: String) {
        NSLog("%s: %s", TAG, "startAdvertising")
        if (peripheralManager?.state == CBCentralManagerStatePoweredOn) {
            NSLog("%s: %s", TAG, "startAdvertising if CBCentralManagerStatePoweredOn")

            // Setup local GATT Server structure
            localCharacteristic = CBMutableCharacteristic(
                type = MESSAGE_CHAR_UUID,
                properties = CBCharacteristicPropertyWrite or CBCharacteristicPropertyNotify,
                value = null,
                permissions = CBAttributePermissionsWriteable
            )

            val service = CBMutableService(type = SERVICE_UUID, primary = true).apply {
                setCharacteristics(listOf(localCharacteristic))
            }

            peripheralManager?.removeAllServices()
            peripheralManager?.addService(service)

            // Start advertising local metadata
            val advertisementData: Map<Any?, *> = mapOf(
                CBAdvertisementDataServiceUUIDsKey to listOf(SERVICE_UUID),
                CBAdvertisementDataLocalNameKey to username
            )
            peripheralManager?.startAdvertising(advertisementData)
        }
    }

    override fun connectToPeer(peerId: String) {
        // Look up the discovered peripheral from cache reference
        val peripheral = _discoveredPeers.value.find { it.id == peerId }?.nativeRef as? CBPeripheral ?: return

        _connectionState.value = ConnectionState.Connecting(deviceId = peerId)
        centralManager?.connectPeripheral(peripheral, options = null)
    }

    @OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)
    override fun sendMessage(message: String, peerId: String) {
        val peripheral = connectedPeripherals[peerId] ?: return
        val characteristic = writableCharacteristic ?: return

        // Convert the Kotlin String directly into a clean byte array primitive
        val byteArray = message.encodeToByteArray()

        // Pin down the memory array buffer so GC doesn't shift it,
        // and map it cleanly to native Apple NSData bytes
        val data = byteArray.usePinned { pinned ->
            NSData.dataWithBytes(
                bytes = pinned.addressOf(0),
                length = byteArray.size.toULong()
            )
        }

        val rawWriteType = 0L as Any

        peripheral.writeValue(
            data = data,
            forCharacteristic = characteristic,
            type = rawWriteType as Long
        )
    }

    override fun stopAll() {
        centralManager?.stopScan()
        peripheralManager?.stopAdvertising()
        connectedPeripherals.values.forEach { centralManager?.cancelPeripheralConnection(it) }
        connectedPeripherals.clear()
        _discoveredPeers.value = emptyList()
        _connectionState.value = ConnectionState.Disconnected()
    }

    // central delegate - handles scanning results & client connection states
    private inner class CentralDelegate : NSObject(), CBCentralManagerDelegateProtocol {

        override fun centralManagerDidUpdateState(central: CBCentralManager) {}

        override fun centralManager(central: CBCentralManager, didDiscoverPeripheral: CBPeripheral, advertisementData: Map<Any?, *>, RSSI: platform.Foundation.NSNumber) {
            val name = advertisementData[CBAdvertisementDataLocalNameKey] as? String ?: didDiscoverPeripheral.name ?: "Unknown BLE Device"
            val uuidString = didDiscoverPeripheral.identifier.UUIDString

            val currentList = _discoveredPeers.value.toMutableList()
            // Avoid duplicates, update tracking params
            currentList.removeAll { it.id == uuidString }
            currentList.add(
                BluetoothPeer(
                    id = uuidString,
                    name = name,
                    rssi = RSSI.intValue,
                    lastSeen = platform.Foundation.NSDate().timeIntervalSince1970.toLong(),
                    isConnectable = true,
                    nativeRef = didDiscoverPeripheral
                )
            )
            _discoveredPeers.value = currentList
        }

        override fun centralManager(central: CBCentralManager, didConnectPeripheral: CBPeripheral) {
            val uuid = didConnectPeripheral.identifier.UUIDString
            connectedPeripherals[uuid] = didConnectPeripheral

            // Find the peer object from discovered list to fulfill the data class parameter
            val peer = _discoveredPeers.value.find { it.id == uuid }

            if (peer != null) {
                // Move into DiscoveringServices state because CoreBluetooth requires explicit service discovery next
                _connectionState.value = ConnectionState.DiscoveringServices(deviceId = uuid)
            }

            didConnectPeripheral.delegate = PeripheralDiscoverDelegate()
            didConnectPeripheral.discoverServices(listOf(SERVICE_UUID))
        }

        override fun centralManager(central: CBCentralManager, didDisconnectPeripheral: CBPeripheral, error: platform.Foundation.NSError?) {
            val uuid = didDisconnectPeripheral.identifier.UUIDString
            connectedPeripherals.remove(uuid)
            _connectionState.value = ConnectionState.Disconnected(message = error?.localizedDescription)
        }
    }

    private inner class PeripheralDiscoverDelegate : NSObject(), CBPeripheralDelegateProtocol {
        override fun peripheral(peripheral: CBPeripheral, didDiscoverServices: platform.Foundation.NSError?) {
            val service = peripheral.services?.firstOrNull { (it as CBService).UUID == SERVICE_UUID } as? CBService
            if (service != null) {
                peripheral.discoverCharacteristics(listOf(MESSAGE_CHAR_UUID), forService = service)
            } else {
                _connectionState.value = ConnectionState.Disconnected("Required BLE Service not found.")
            }
        }

        override fun peripheral(peripheral: CBPeripheral, didDiscoverCharacteristicsForService: CBService, error: platform.Foundation.NSError?) {
            val char = didDiscoverCharacteristicsForService.characteristics?.firstOrNull { (it as CBCharacteristic).UUID == MESSAGE_CHAR_UUID } as? CBCharacteristic
            val uuid = peripheral.identifier.UUIDString

            if (char != null) {
                writableCharacteristic = char // Cache characteristic

                val peer = _discoveredPeers.value.find { it.id == uuid }
                if (peer != null) {
                    // ready for chat
                    _connectionState.value = ConnectionState.Connected(peer = peer)
                }
            } else {
                _connectionState.value = ConnectionState.Disconnected("Required BLE Characteristic not found.")
            }
        }
    }

    // peripheral manager delegate - handles local gatt server requests
    private inner class PeripheralDelegate : NSObject(), CBPeripheralManagerDelegateProtocol {
        override fun peripheralManagerDidUpdateState(peripheral: CBPeripheralManager) {
            val state = peripheral.state
            NSLog("%s: %s", TAG, "peripheralManagerDidUpdateState")

            if (state == 4L) {
                NSLog("%s: iOS Hardware powered on successfully! Auto-triggering advertising layout.", TAG)
                // Trigger the advertisement setup
                startAdvertising("Predrag_iPhone")
            } else {
                NSLog("%s: iOS Hardware is not ready yet. State code: %d", TAG, state)
            }
        }

        override fun peripheralManagerDidStartAdvertising(peripheral: CBPeripheralManager, error: NSError?) {
            NSLog("%s: %s", TAG, "peripheralManagerDidStartAdvertising: error: $error")
        }

        // Mirrors other device onCharacteristicWriteRequest
        override fun peripheralManager(peripheral: CBPeripheralManager, didReceiveWriteRequests: List<*>) {
            NSLog("%s: %s", TAG, "peripheralManager")

            for (request in didReceiveWriteRequests) {
                val cbRequest = request as? CBATTRequest ?: continue

                if (cbRequest.characteristic.UUID == MESSAGE_CHAR_UUID) {
                    val data = cbRequest.value
                    if (data != null) {
                        val stringMessage = platform.Foundation.NSString.create(data = data, encoding = platform.Foundation.NSUTF8StringEncoding)?.toString() ?: ""

                        // Emit the incoming data via SharedFlow pipeline!
//                        _incomingMessages.tryEmit(Message(text = stringMessage, senderId = cbRequest.central.identifier.UUIDString))
                    }
                    // Acknowledge receipt to the sender client
                    peripheralManager?.respondToRequest(cbRequest, withResult = CBATTErrorSuccess)
                } else {
                    peripheralManager?.respondToRequest(cbRequest, withResult = CBATTErrorRequestNotSupported)
                }
            }
        }
    }

    companion object {
        private const val TAG = "z&zIosBluetoothManager"
    }
}