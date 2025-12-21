package com.example.bluetoothmessenger

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bluetoothmessenger.data.SettingsRepository
import com.example.bluetoothmessenger.ui.*
import com.example.bluetoothmessenger.ui.theme.BluetoothMessengerTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: ChatViewModel by viewModels { ChatViewModel.Factory }
    private lateinit var settingsRepository: SettingsRepository

    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val canEnableBluetooth = permissions[Manifest.permission.BLUETOOTH_CONNECT] == true
        val canScan = permissions[Manifest.permission.BLUETOOTH_SCAN] == true
        
        if (canEnableBluetooth && canScan) {
           //permissions granted
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        settingsRepository = (application as BluetoothMessengerApplication).container.settingsRepository

        checkBluetoothPermissions()
        
        //start foreground service to keep app alive
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(android.content.Intent(this, BluetoothBackgroundService::class.java))
        } else {
            startService(android.content.Intent(this, BluetoothBackgroundService::class.java))
        }

        setContent {
            val darkMode by settingsRepository.darkModeFlow.collectAsStateWithLifecycle(initialValue = false)
            
            BluetoothMessengerTheme(darkTheme = darkMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val state by viewModel.state.collectAsState()
                    
                    MainNavigationHost(
                        state = state,
                        darkMode = darkMode,
                        onDarkModeToggle = { enabled ->
                            settingsRepository.setDarkMode(enabled)
                        },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavigationHost(
    state: ChatUiState,
    darkMode: Boolean,
    onDarkModeToggle: (Boolean) -> Unit,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnect: (com.example.bluetoothmessenger.data.BluetoothDeviceDomain) -> Unit,
    onSendMessage: (String, String) -> Unit,
    onCloseConnection: (String) -> Unit,
    onDeleteMessage: (String, com.example.bluetoothmessenger.data.BluetoothMessage) -> Unit
) {
    var showSettings by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Bluetooth Messenger",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Settings, null) },
                        label = { Text("Settings") },
                        selected = false,
                        onClick = {
                            showSettings = true
                            scope.launch { drawerState.close() }
                        }
                    )
                    
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Menu, null) },
                        label = { Text("Archive") },
                        selected = false,
                        onClick = {
                            // Archive not yet implemented
                            scope.launch { drawerState.close() }
                        }
                    )
                }
            }
        }
    ) {
        if (showSettings) {
            SettingsScreen(
                darkMode = darkMode,
                onDarkModeToggle = onDarkModeToggle,
                onBackClick = { showSettings = false }
            )
        } else {
            // Main Tabbed Interface
            Scaffold(
                topBar = {
                    //only show simple top bar if we want a menu button always visible
                    //but MainScreenRoot has its own tabs as top bar.
                    //gotta need to inject the menu button into MainScreenRoot or wrap it.
                    //MainScreenRoot uses ScrollableTabRow at the top.
                    TopAppBar(
                        title = { Text("Bluetooth Messenger") },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu")
                            }
                        }
                    )
                }
            ) { padding ->
                Box(modifier = Modifier.padding(padding)) {
                    MainScreenRoot(
                        state = state,
                        onStartScan = onStartScan,
                        onStopScan = onStopScan,
                        onConnect = onConnect,
                        onSendMessage = onSendMessage,
                        onCloseConnection = onCloseConnection,
                        onDeleteMessage = onDeleteMessage
                    )
                }
            }
        }
    }
}

sealed class Screen {
    object ConversationList : Screen()
    data class Chat(val address: String) : Screen()
    object DeviceDiscovery : Screen()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreenWithTopBar(
    conversations: List<com.example.bluetoothmessenger.data.ConversationItem>,
    onConversationClick: (String) -> Unit,
    onNewDeviceClick: () -> Unit,
    onMenuClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bluetooth Messenger") },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Default.Menu, "Menu")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            ConversationListScreen(
                conversations = conversations,
                onConversationClick = onConversationClick,
                onNewDeviceClick = onNewDeviceClick
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreenWithTopBar(
    messages: List<com.example.bluetoothmessenger.data.BluetoothMessage>,
    deviceName: String,
    onSendMessage: (String) -> Unit,
    onDeleteMessage: (com.example.bluetoothmessenger.data.BluetoothMessage) -> Unit,
    onBackClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(deviceName) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.Menu, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            ChatScreen(
                messages = messages,
                onSendMessage = onSendMessage,
                onDeleteMessage = onDeleteMessage,
                deviceName = deviceName
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceListScreenWithTopBar(
    state: ChatUiState,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnect: (com.example.bluetoothmessenger.data.BluetoothDeviceDomain) -> Unit,
    onBackClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Find Devices") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.Menu, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            DeviceListScreen(
                state = state,
                onStartScan = onStartScan,
                onStopScan = onStopScan,
                onConnect = onConnect
            )
        }
    }
}