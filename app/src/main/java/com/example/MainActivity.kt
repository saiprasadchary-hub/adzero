package com.example

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.data.model.VpnConnectionState
import com.example.feature.navigation.AdZeroAppRoot
import com.example.service.traffic.NetworkTrafficMonitorService
import com.example.service.vpn.AdZeroVpnService
import com.example.ui.theme.AdZeroTheme

class MainActivity : FragmentActivity() {

    private val appContainer by lazy { (application as AdZeroApplication).container }

    private val vpnConsentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            AdZeroVpnService.start(this)
        } else {
            appContainer.protectionStateHolder.setState(
                state = VpnConnectionState.ERROR,
                errorMessage = "VPN permission was declined. AdZero cannot filter DNS without local VPN authorization."
            )
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Proceed with VPN start whether notification permission is granted or denied
        launchVpnConsentOrStart()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )

        setContent {
            AdZeroTheme {
                AdZeroAppRoot(
                    container = appContainer,
                    onToggleVpnProtection = ::handleToggleVpnProtection,
                    onPauseOrResumeVpn = ::handlePauseOrResumeVpn
                )
            }
        }

        if (intent?.getBooleanExtra(EXTRA_REQUEST_VPN_START, false) == true) {
            handleToggleVpnProtection()
        }
    }

    override fun onStart() {
        super.onStart()
        NetworkTrafficMonitorService.start(this)
    }

    override fun onStop() {
        super.onStop()
        if (!appContainer.protectionStateHolder.snapshot.value.isRunning) {
            NetworkTrafficMonitorService.stop(this)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_REQUEST_VPN_START, false)) {
            handleToggleVpnProtection()
        }
    }

    private fun handleToggleVpnProtection() {
        val currentState = appContainer.protectionStateHolder.snapshot.value.state
        if (currentState == VpnConnectionState.ACTIVE ||
            currentState == VpnConnectionState.PAUSED ||
            currentState == VpnConnectionState.CONNECTING
        ) {
            AdZeroVpnService.stop(this)
            return
        }

        // Request POST_NOTIFICATIONS gracefully on Android 13+ so foreground status notification is visible
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        launchVpnConsentOrStart()
    }

    private fun launchVpnConsentOrStart() {
        val prepareIntent = VpnService.prepare(this)
        if (prepareIntent != null) {
            appContainer.protectionStateHolder.setState(VpnConnectionState.REQUESTING_PERMISSION)
            vpnConsentLauncher.launch(prepareIntent)
        } else {
            AdZeroVpnService.start(this)
        }
    }

    private fun handlePauseOrResumeVpn() {
        val currentState = appContainer.protectionStateHolder.snapshot.value.state
        when (currentState) {
            VpnConnectionState.ACTIVE -> AdZeroVpnService.pause(this)
            VpnConnectionState.PAUSED -> AdZeroVpnService.resume(this)
            else -> Unit
        }
    }

    companion object {
        const val EXTRA_REQUEST_VPN_START = "com.example.adzero.EXTRA_REQUEST_VPN_START"
    }
}
