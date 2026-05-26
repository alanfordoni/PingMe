package com.spbalkan.pingme.home.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spbalkan.pingme.ble.BlePermissionState
import com.spbalkan.pingme.home.viewmodel.BlePermissionViewModel
import com.spbalkan.pingme.home.viewmodel.HomeViewModel
import com.spbalkan.pingme.models.BluetoothPeer
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = koinViewModel(),
    bleViewModel: BlePermissionViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val permissionState by bleViewModel.permissionState.collectAsState()
    val bluetoothState  by bleViewModel.bluetoothState.collectAsState()

    when {
        permissionState !is BlePermissionState.Granted -> {
            bleViewModel.requestPermissions()
            HomeBleNotReadyContent()
            // ToDo cover all states
        }

        !bluetoothState -> {
            bleViewModel.openBluetoothSettings()
            HomeBleNotReadyContent()
        }

        else -> {
            HomeScreenContent(
                state = state,
                onIntent = viewModel::onAction
            )
        }
    }
}

// Todo WIP!
@Composable
fun HomeBleNotReadyContent() {
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
    ) { paddingValues ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(all = 16.dp)
        ) {
            Text(modifier = Modifier
                .align(Alignment.Center),
                text = "Bluetooth not ready"
            )
        }
    }
}

@Composable
fun HomeScreenContent(
    state: HomeViewModel.HomeState,
    onIntent: (HomeViewModel.ActionIntents) -> Unit
) {
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
    ) { paddingValues ->


        Box(modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(all = 16.dp)
        ) {
            Button(modifier = Modifier
                .align(Alignment.TopStart)
                .padding(vertical = 16.dp),
                onClick = {
                    onIntent(HomeViewModel.ActionIntents.OnStartScan)
                }
            ) {
                Text(text = "Start scanning!")
            }

            Button(modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(vertical = 16.dp),
                onClick = {
                    onIntent(HomeViewModel.ActionIntents.OnStartAdvertising)
                }
            ) {
                Text(text = "Start advertising!")
            }

            LazyColumn(modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
            ) {
                items(state.devicesList) { item ->
                    BleDeviceItem(item)
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }

}

@Composable
fun BleDeviceItem(bluetoothPeer: BluetoothPeer) {
    Column(modifier = Modifier
        .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = bluetoothPeer.id
        )

        Text(
            text = bluetoothPeer.name
        )

        Text(
            text = bluetoothPeer.rssi.toString()
        )

        Text(
            text = bluetoothPeer.isConnectable.toString()
        )

    }
}