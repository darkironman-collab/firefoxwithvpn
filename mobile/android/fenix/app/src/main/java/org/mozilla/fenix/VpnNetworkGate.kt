/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Process-wide VPN gate.
 *
 * Firefox is bound to Android's validated default VPN before Gecko starts. If that VPN disappears,
 * [FenixApplication] terminates the process so Gecko, downloads, and workers cannot silently fall
 * back to Wi-Fi or mobile data.
 */
object VpnNetworkGate {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArraySet<(Boolean) -> Unit>()
    private var callback: ConnectivityManager.NetworkCallback? = null

    fun isValidatedVpn(context: Context): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /**
     * Pins every socket created by this process to the validated VPN network.
     */
    fun bindToValidatedVpn(context: Context): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        val allowed = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        return allowed && manager.bindProcessToNetwork(network)
    }

    @Synchronized
    fun start(context: Context, onVpnLost: () -> Unit) {
        listeners.add { connected ->
            if (!connected) {
                onVpnLost()
            }
        }
        if (callback != null) {
            notifyListeners(context)
            return
        }

        val manager = context.getSystemService(ConnectivityManager::class.java)
        callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = scheduleCheck(context)
            override fun onLost(network: Network) = scheduleCheck(context)
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) =
                scheduleCheck(context)
        }.also(manager::registerDefaultNetworkCallback)
        notifyListeners(context)
    }

    fun addListener(listener: (Boolean) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (Boolean) -> Unit) {
        listeners.remove(listener)
    }

    private fun scheduleCheck(context: Context) {
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.postDelayed({ notifyListeners(context) }, DISCONNECT_DEBOUNCE_MS)
    }

    private fun notifyListeners(context: Context) {
        val connected = isValidatedVpn(context)
        if (connected) {
            bindToValidatedVpn(context)
        }
        listeners.forEach { it(connected) }
    }

    private const val DISCONNECT_DEBOUNCE_MS = 250L
}
