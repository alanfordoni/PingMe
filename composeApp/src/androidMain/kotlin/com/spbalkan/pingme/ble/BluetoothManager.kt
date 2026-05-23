package com.spbalkan.pingme.ble

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattCharacteristic.PERMISSION_WRITE
import android.bluetooth.BluetoothGattCharacteristic.PROPERTY_NOTIFY
import android.bluetooth.BluetoothGattCharacteristic.PROPERTY_WRITE
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothGattService.SERVICE_TYPE_PRIMARY
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import com.spbalkan.pingme.models.BluetoothPeer
import com.spbalkan.pingme.models.Message
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class AndroidBluetoothManager(
    private val context: Context
) : BluetoothManager {
    private val SERVICE_UUID = UUID.fromString("6eb67544-cf6a-4fd9-b951-69a47342f741")
    private val MESSAGE_CHAR_UUID: UUID = UUID.fromString("12345678-1234-5678-1234-567812345678") // Custom Message UUID
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
    private val adapter = bluetoothManager.adapter
    private var gattServer: BluetoothGattServer? = null

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected())
    override val connectionState = _connectionState.asStateFlow()

    private val _discoveredPeers = MutableStateFlow<List<BluetoothPeer>>(emptyList())
    override val discoveredPeers = _discoveredPeers.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<Message>()
    override val incomingMessages = _incomingMessages.asSharedFlow()

    private val bluetoothLeScanner = adapter.bluetoothLeScanner
    private var isScanning = false
    private val handler = Handler(Looper.getMainLooper())

    // Stops scanning after 10 seconds.
    private val SCAN_PERIOD: Long = 10000

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    override fun startScanning() {
        if (!isScanning) {
            isScanning = true

            val filter = ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(SERVICE_UUID))
                .build()
            val filterList = listOf(filter)

            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            bluetoothLeScanner.startScan(filterList, settings, scanCallback)

            handler.postDelayed({
                if (isScanning) {
                    bluetoothLeScanner.stopScan(scanCallback)
                    isScanning = false
                    Log.d(TAG, "Scan period completed. Scanner stopped automatically.")
                }
            }, SCAN_PERIOD)
        }

    }

    private val scanCallback: ScanCallback = object : ScanCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)

            val serviceUuids = result.scanRecord?.serviceUuids

            // Check if custom app UUID is inside ParcelUuid list
            val isOurAppPeer = serviceUuids?.contains(ParcelUuid(SERVICE_UUID)) == true
            // Reject the device if it's not running this app
            if (!isOurAppPeer) {
                return
            }

            val deviceName = result.scanRecord?.deviceName ?: result.device.name ?: "FindBeacon Peer"
            val deviceAddress = result.device.address
            Log.d(TAG, "onScanResult: Found device: $deviceName ($deviceAddress)")

            val updatedList = _discoveredPeers.value.toMutableList()
            updatedList.removeAll { it.id == deviceAddress }
            updatedList.add(BluetoothPeer(
                id = deviceAddress,
                name = deviceName,
                rssi = result.rssi,
                lastSeen = result.timestampNanos,
                isConnectable = result.isConnectable,
                nativeRef = result.device
            ))
            _discoveredPeers.value = updatedList
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.e(TAG, "onScanFailed: error: $errorCode")
            isScanning = false
        }
    }

    override fun sendMessage(message: String, peerId: String) {
        /*val gatt = connectedGatts[peerId] ?: return
        val char = gatt.getService(SERVICE_UUID)
            ?.getCharacteristic(MESSAGE_CHAR_UUID) ?: return
        char.value = message.toByteArray(Charsets.UTF_8)
        gatt.writeCharacteristic(char)*/
    }

    override fun stopAll() {
        // ToDo
    }

    override fun isEnabled(): Boolean {
        return adapter.isEnabled
    }

    override fun startAdvertising(username: String) {
        Log.d(TAG, "startAdvertising: ")

        val advertiser = adapter.bluetoothLeAdvertiser
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .build()

        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .setIncludeDeviceName(true)
            .build()

        advertiser.startAdvertising(settings, data, object: AdvertiseCallback() {

        })
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            setupGattServer(username)
        }
    }

    // store active client links
    private val connectedGatts = mutableMapOf<String, BluetoothGatt>()

    override fun connectToPeer(peerId: String) {
        Log.d(TAG, "connectToPeer: peerID: $peerId")
        // stop scanning before connecting
        if (isScanning) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                bluetoothLeScanner.stopScan(scanCallback)
                isScanning = false
            }
        }

        // Get the hardware device pointer and establish a GATT Client connection
        // peerId is the MAC address
        val device = adapter.getRemoteDevice(peerId)
        _connectionState.value = ConnectionState.Connecting(peerId)
        // Connect to the remote device's GATT server wrapper
        val gatt = device.connectGatt(context, false, gattClientCallback, BluetoothDevice.TRANSPORT_LE)
        connectedGatts[peerId] = gatt
    }

    private val gattClientCallback = object : android.bluetooth.BluetoothGattCallback() {

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            val deviceAddress = gatt.device.address
            val peerFromFoundDevices = _discoveredPeers.value.find { it.id == deviceAddress }
            var peer: BluetoothPeer

            peerFromFoundDevices?.let {
                peer = it
            } ?: run {
                peer = BluetoothPeer(
                    id = deviceAddress,
                    name = gatt.device.name ?: "Unknown Remote Peripheral",
                    rssi = -100,
                    lastSeen = System.nanoTime(),
                    isConnectable = true,
                    nativeRef = gatt.device
                )
            }

            if (status == BluetoothGatt.GATT_SUCCESS) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        Log.d(TAG, "GATT Client Connected to: $deviceAddress")
                        _connectionState.value = ConnectionState.Connected(peer)

                        // connected, but cannot write data yet
                        // discover services to map remote characteristics
                        Log.d(TAG, "Discovering remote services...")
                        gatt.discoverServices()
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Log.d(TAG, "GATT Client Disconnected from: $deviceAddress")
                        _connectionState.value = ConnectionState.Disconnected()
                        connectedGatts.remove(deviceAddress)
                        gatt.close()
                    }
                }
            } else {
                Log.e(TAG, "GATT Client connection status error: $status. Disconnecting...")
                _connectionState.value = ConnectionState.Disconnected()
                connectedGatts.remove(deviceAddress)
                gatt.close()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            super.onServicesDiscovered(gatt, status)
            val deviceAddress = gatt.device.address

            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Services successfully discovered on remote peer: $deviceAddress")

                // Optional Verification: Double-check if target service exists
                val targetService = gatt.getService(SERVICE_UUID)
                if (targetService != null) {
                    Log.d(TAG, "Target BLE service found! Ready to transmit messages.")
                } else {
                    Log.w(TAG, "Connected device does not expose matching service UUID.")
                }
            } else {
                Log.e(TAG, "Remote service discovery failed with status: $status")
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Data successfully written to characteristic: ${characteristic.uuid}")
            } else {
                Log.e(TAG, "Failed writing data to characteristic. Status error: $status")
            }
        }

        @Deprecated("Deprecated in modern Android SDKs, handling for backwards compatibility support")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            super.onCharacteristicChanged(gatt, characteristic)
            processIncomingNotification(characteristic.value, gatt.device.address)
        }

        // Modern SDK API 33+ explicit callback signature override
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            super.onCharacteristicChanged(gatt, characteristic, value)
            processIncomingNotification(value, gatt.device.address)
        }
    }

    // Helper logic block to route data notifications cleanly into shared KMP Flow
    private fun processIncomingNotification(bytes: ByteArray, senderAddress: String) {
        val incomingString = String(bytes, Charsets.UTF_8)
        Log.d(TAG, "Received subscription data from $senderAddress: $incomingString")

        // Launch a coroutine scope to emit down into shared UI structure
        // example: _incomingMessages.tryEmit(Message(text = incomingString, senderId = senderAddress))
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun setupGattServer(username: String) {

        gattServer = bluetoothManager.openGattServer(context, gattServerCallback).apply {
            val service = BluetoothGattService(SERVICE_UUID, SERVICE_TYPE_PRIMARY)
            val char = BluetoothGattCharacteristic(MESSAGE_CHAR_UUID, PROPERTY_WRITE or PROPERTY_NOTIFY, PERMISSION_WRITE)
            service.addCharacteristic(char)
            addService(service)
        }
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            super.onConnectionStateChange(device, status, newState)

            if (status == BluetoothGatt.GATT_SUCCESS) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        Log.d(TAG, "Device connected: ${device.address}")
                        // Optionally keep track of connected devices to send notifications later
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Log.d(TAG, "Device disconnected: ${device.address}")
                    }
                }
            } else {
                Log.e(TAG, "Connection error status: $status")
            }
        }

        // data written by characteristic by a remote central device
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value)

            // Verify that write is meant for specific message characteristic
            if (characteristic.uuid == MESSAGE_CHAR_UUID) {
                val receivedMessage = String(value, Charsets.UTF_8)
                Log.d(TAG, "Received message from ${device.address}: $receivedMessage")

                // ToDo Pass this 'receivedMessage' back to KMP common code layer via a flow or callback!

                // Send a response back to the client if it requested acknowledgment
                if (responseNeeded) {
                    gattServer?.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        offset,
                        value
                    )
                }
            } else {
                if (responseNeeded) {
                    gattServer?.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED,
                        offset,
                        null
                    )
                }
            }
        }
    }

    companion object {
        const val TAG = "z&zAndBleManager"
    }
}