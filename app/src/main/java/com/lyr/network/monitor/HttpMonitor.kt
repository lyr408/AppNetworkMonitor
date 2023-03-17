package com.lyr.network.monitor

import java.net.InetAddress
import java.net.URI
import java.net.URISyntaxException

object HttpMonitor {
    fun isLocalhost(url: String?): Boolean {
        var isLocal = false
        try {
            val uri = URI(url)
            val host = uri.host
            if (host == "localhost") {
                isLocal = true
            } else {
                val addr = InetAddress.getByName(host)
                if (addr.isLoopbackAddress) {
                    isLocal = true
                }
            }
        } catch (e: URISyntaxException) {
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return isLocal
    }
}