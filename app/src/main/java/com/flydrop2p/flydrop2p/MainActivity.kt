package com.flydrop2p.flydrop2p

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.flydrop2p.flydrop2p.domain.repository.OwnAccountRepository
import com.flydrop2p.flydrop2p.domain.repository.OwnProfileRepository
import com.flydrop2p.flydrop2p.network.BackupApi
import com.flydrop2p.flydrop2p.network.NetworkManager
import com.flydrop2p.flydrop2p.ui.FlyDropApp
import com.flydrop2p.flydrop2p.ui.theme.FlyDropTheme
import com.flydrop2p.flydrop2p.notification.NotificationHelper
import kotlinx.coroutines.launch
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    private val intentFilter = IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
    }

    private lateinit var ownAccountRepository: OwnAccountRepository
    private lateinit var ownProfileRepository: OwnProfileRepository
    private lateinit var networkManager: NetworkManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        (application as App).initializeContainer(this)

        requestPermissions()
        checkServices()

        ownAccountRepository = (application as App).container.ownAccountRepository
        ownProfileRepository = (application as App).container.ownProfileRepository
        networkManager = (application as App).container.networkManager

        lifecycleScope.launch {
            if(ownAccountRepository.getAccount().accountId == 0L) {
                val id = Build.MODEL.hashCode().toLong()

                ownAccountRepository.setAccountId(id)
                ownProfileRepository.setAccountId(id)
            }

            networkManager.startConnections()
            networkManager.startDiscoverPeersHandler()
            networkManager.startSendKeepaliveHandler()
            networkManager.startUpdateConnectedDevicesHandler()
        }

        setContent {
            FlyDropTheme {
                val startAccountId = intent?.getLongExtra(NotificationHelper.KEY_CHAT_ACCOUNT_ID, -1L)
                val startGroup = intent?.getStringExtra(NotificationHelper.KEY_GROUP_NAME)
                FlyDropApp(
                    startChatAccountId = startAccountId?.takeIf { it != -1L },
                    startGroupName = startGroup
                )
            }
        }

        // Handle call action if activity was started from a notification while not running
        handleCallActionIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        Log.d("MainActivity", "onStart")
    }

    override fun onResume() {
        super.onResume()
        Log.d("MainActivity", "onResume")

        registerReceiver(networkManager.receiver, intentFilter)
    }

    override fun onPause() {
        super.onPause()
        Log.d("MainActivity", "onPause")

        unregisterReceiver(networkManager.receiver)
    }

    override fun onStop() {
        super.onStop()
        Log.d("MainActivity", "onStop")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("MainActivity", "onDestroy")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleCallActionIntent(intent)

        // Navigate to group chat if present
        val startGroup = intent.getStringExtra(NotificationHelper.KEY_GROUP_NAME)
        if (startGroup != null) {
            setContent {
                FlyDropTheme {
                    FlyDropApp(startGroupName = startGroup)
                }
            }
        }
    }

    private fun handleCallActionIntent(intent: Intent?) {
        val action = intent?.getStringExtra(NotificationHelper.KEY_CALL_ACTION)
        val callAccountId = intent?.getLongExtra(NotificationHelper.KEY_CALL_ACCOUNT_ID, -1L) ?: -1L
        if (action != null && callAccountId != -1L) {
            // Cancel the incoming call notification immediately
            (application as App).container.notificationHelper.cancelIncomingCallNotification(callAccountId)

            lifecycleScope.launch {
                when (action) {
                    NotificationHelper.ACTION_ACCEPT -> networkManager.sendCallResponse(callAccountId, true)
                    NotificationHelper.ACTION_DECLINE -> networkManager.sendCallResponse(callAccountId, false)
                }
            }
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf<String>()

        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_DENIED) {
            if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {

            }

            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_DENIED) {
                if (shouldShowRequestPermissionRationale(Manifest.permission.NEARBY_WIFI_DEVICES)) {

                }

                permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_DENIED) {
                if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {

                }

                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_DENIED) {
            if (shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {

            }

            permissions.add(Manifest.permission.RECORD_AUDIO)
        }

        if (permissions.isNotEmpty()) {
            val permissionsResultLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {

            }

            permissionsResultLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun checkServices() {
        val wifiManager = getSystemService(Context.WIFI_SERVICE) as WifiManager

        if(!wifiManager.isWifiEnabled) {
            Toast.makeText(this, "Please activate Wi-Fi", Toast.LENGTH_LONG).show()
        }

        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        if(!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Toast.makeText(this, "Please activate location", Toast.LENGTH_LONG).show()
        }
    }
}
