package com.example.bluetoothmessenger

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.bluetoothmessenger.ui.ChatViewModel
import com.example.bluetoothmessenger.ui.MainScreenRoot
import com.example.bluetoothmessenger.ui.ChatUiState
import com.example.bluetoothmessenger.ui.theme.BluetoothMessengerTheme

class MainActivity : ComponentActivity() {

    private val viewModel: ChatViewModel by viewModels { ChatViewModel.Factory }

    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val canEnableBluetooth = permissions[Manifest.permission.BLUETOOTH_CONNECT] == true
        val canScan = permissions[Manifest.permission.BLUETOOTH_SCAN] == true
        
        if (canEnableBluetooth && canScan) {
           viewModel.startScan() // Auto scan on start if permissions granted? Or wait for user.
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Check permissions on start
        checkBluetoothPermissions()
        
        // Start Foreground Service to keep app alive
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(android.content.Intent(this, BluetoothBackgroundService::class.java))
        } else {
            startService(android.content.Intent(this, BluetoothBackgroundService::class.java))
        }

        setContent {
            BluetoothMessengerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val state by viewModel.state.collectAsState()
                    
                    MainScreenRoot(
                        state = state,
                        onStartScan = viewModel::startScan,
                        onStopScan = viewModel::stopScan,
                        onConnect = viewModel::connectToDevice,
                        onSendMessage = viewModel::sendMessage,
                        onCloseConnection = viewModel::disconnectFromDevice,
                        onDeleteMessage = viewModel::deleteMessage
                    )
                }
            }
        }
    }

    private fun checkBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            bluetoothPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_ADVERTISE
                )
            )
        } else {
            bluetoothPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }
}