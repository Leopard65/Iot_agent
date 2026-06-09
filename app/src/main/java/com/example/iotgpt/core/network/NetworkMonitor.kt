package com.example.iotgpt.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Observes device network connectivity for dashboard status.
 */
class NetworkMonitor(
    private val context: Context
) {
    fun observeNetworkStatus(): Flow<NetworkStatus> = callbackFlow {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)

        fun sendCurrentStatus() {
            trySend(connectivityManager.currentStatus())
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                sendCurrentStatus()
            }

            override fun onLost(network: Network) {
                sendCurrentStatus()
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                sendCurrentStatus()
            }
        }

        sendCurrentStatus()
        connectivityManager.registerNetworkCallback(
            NetworkRequest.Builder().build(),
            callback
        )

        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }

    private fun ConnectivityManager.currentStatus(): NetworkStatus {
        val network = activeNetwork ?: return NetworkStatus(false, "无网络")
        val capabilities = getNetworkCapabilities(network) ?: return NetworkStatus(false, "无网络")
        val type = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "蜂窝网络"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "以太网"
            else -> "已连接"
        }
        val connected = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        return NetworkStatus(connected, type)
    }
}

data class NetworkStatus(
    val isConnected: Boolean,
    val label: String
)
