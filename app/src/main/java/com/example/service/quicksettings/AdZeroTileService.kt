package com.example.service.quicksettings

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.example.AdZeroApplication
import com.example.MainActivity
import com.example.data.model.VpnConnectionState
import com.example.service.vpn.AdZeroVpnService

/**
 * Android Quick Settings TileService that truthfully mirrors AdZeroVpnService active state.
 * Never marks the tile active until VpnService.Builder.establish() has succeeded.
 */
class AdZeroTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        syncTileUi()
    }

    override fun onClick() {
        super.onClick()
        val app = application as? AdZeroApplication ?: return
        val currentState = app.container.protectionStateHolder.snapshot.value.state

        if (currentState == VpnConnectionState.ACTIVE || currentState == VpnConnectionState.PAUSED) {
            AdZeroVpnService.stop(this)
            syncTileUi(forceInactive = true)
            return
        }

        val prepareIntent = VpnService.prepare(this)
        if (prepareIntent == null) {
            // Permission already granted -> start local VPN directly
            AdZeroVpnService.start(this)
            qsTile?.let { tile ->
                tile.state = Tile.STATE_INACTIVE
                tile.label = "AdZero"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = "Connecting…"
                }
                tile.updateTile()
            }
        } else {
            // VPN consent dialog requires an Activity -> launch MainActivity cleanly
            val launchIntent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(MainActivity.EXTRA_REQUEST_VPN_START, true)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pi = PendingIntent.getActivity(
                    this,
                    102,
                    launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                startActivityAndCollapse(pi)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(launchIntent)
            }
        }
    }

    private fun syncTileUi(forceInactive: Boolean = false) {
        val tile = qsTile ?: return
        val app = application as? AdZeroApplication
        val snap = app?.container?.protectionStateHolder?.snapshot?.value
        val active = !forceInactive && snap?.state == VpnConnectionState.ACTIVE

        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "AdZero"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = when {
                active -> "Protected"
                snap?.state == VpnConnectionState.PAUSED -> "Paused"
                snap?.state == VpnConnectionState.CONNECTING -> "Connecting…"
                else -> "Off"
            }
        }
        tile.contentDescription = if (active) "AdZero Protection Active" else "AdZero Protection Inactive"
        tile.updateTile()
    }

    companion object {
        fun requestTileSync(context: Context) {
            runCatching {
                requestListeningState(
                    context,
                    ComponentName(context, AdZeroTileService::class.java)
                )
            }
        }
    }
}
