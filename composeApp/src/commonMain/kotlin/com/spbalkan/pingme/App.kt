package com.spbalkan.pingme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import com.spbalkan.pingme.home.ui.HomeScreen

@Composable
fun App() {
    MaterialTheme {
        HomeScreen()
    }
}