package com.example.security

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

enum class NetworkStatus {
  Online,
  Unstable,
  Offline,
}

interface NetworkStatusMonitor {
  fun observeStatus(): Flow<NetworkStatus>
}

class AndroidNetworkMonitor(context: Context) : NetworkStatusMonitor {

  private val appContext = context.applicationContext

  override fun observeStatus(): Flow<NetworkStatus> =
    callbackFlow {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (cm == null) {
          trySend(NetworkStatus.Offline)
          close()
          return@callbackFlow
        }

        trySend(evaluateCurrentStatus(cm))

        val callback =
          object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
              trySend(evaluateNetwork(cm, network, cm.getNetworkCapabilities(network)))
            }

            override fun onCapabilitiesChanged(
              network: Network,
              networkCapabilities: NetworkCapabilities,
            ) {
              trySend(evaluateNetwork(cm, network, networkCapabilities))
            }

            override fun onLosing(network: Network, maxMsToLive: Int) {
              trySend(NetworkStatus.Unstable)
            }

            override fun onLost(network: Network) {
              trySend(evaluateCurrentStatus(cm))
            }

            override fun onUnavailable() {
              trySend(NetworkStatus.Offline)
            }
          }

        val registered =
          runCatching {
              cm.registerDefaultNetworkCallback(callback)
              true
            }
            .getOrElse {
              SafeLogger.w("Failed to register default network callback")
              false
            }

        awaitClose {
          if (registered) {
            runCatching { cm.unregisterNetworkCallback(callback) }
          }
        }
      }
      .distinctUntilChanged()

  private fun evaluateCurrentStatus(cm: ConnectivityManager): NetworkStatus {
    val active = cm.activeNetwork ?: return NetworkStatus.Offline
    return evaluateNetwork(cm, active, cm.getNetworkCapabilities(active))
  }

  private fun evaluateNetwork(
    cm: ConnectivityManager,
    network: Network,
    capabilities: NetworkCapabilities?,
  ): NetworkStatus {
    val caps = capabilities ?: cm.getNetworkCapabilities(network) ?: return NetworkStatus.Offline
    return classifyCapabilities(caps)
  }

  companion object {
    internal const val LOW_BANDWIDTH_KBPS_THRESHOLD = 450

    fun classifyCapabilities(capabilities: NetworkCapabilities): NetworkStatus {
      if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
        return NetworkStatus.Offline
      }
      if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
        return NetworkStatus.Unstable
      }
      if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
          !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_CONGESTED)
      ) {
        return NetworkStatus.Unstable
      }
      val downKbps = capabilities.linkDownstreamBandwidthKbps
      if (downKbps in 1..LOW_BANDWIDTH_KBPS_THRESHOLD) {
        return NetworkStatus.Unstable
      }
      return NetworkStatus.Online
    }
  }
}
