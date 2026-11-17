package com.earthx.explorer.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Exposes whether a validated internet connection is currently available. */
class ConnectivityObserver(context: Context) {
    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val _online = MutableStateFlow(currentlyOnline())
    val online: StateFlow<Boolean> = _online.asStateFlow()
    private val networks = mutableSetOf<Network>()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            synchronized(networks) {
                if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) networks.add(network) else networks.remove(network)
                _online.value = networks.isNotEmpty()
            }
        }
        override fun onLost(network: Network) {
            synchronized(networks) { networks.remove(network); _online.value = networks.isNotEmpty() || currentlyOnline() }
        }
    }

    init {
        runCatching {
            cm.registerNetworkCallback(
                NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(), callback)
        }
    }

    private fun currentlyOnline(): Boolean {
        val n = cm.activeNetwork ?: return false
        val c = cm.getNetworkCapabilities(n) ?: return false
        return c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
