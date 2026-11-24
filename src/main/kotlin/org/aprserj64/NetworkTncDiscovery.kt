// SPDX-License-Identifier: AGPL-3.0-only
package org.aprserj64

import java.net.Inet6Address
import java.net.NetworkInterface
import javax.jmdns.JmDNS
import kotlinx.coroutines.*
import org.aprsfoundation.core.MobileRadio

internal data class NetworkTnc(val name: String, val endpoint: String)

internal suspend fun discoverNetworkTncs(): List<NetworkTnc> = withContext(Dispatchers.IO) {
    val addresses=NetworkInterface.networkInterfaces().toList()
        .filter {it.isUp && !it.isLoopback && it.supportsMulticast()}
        .flatMap {network->network.inetAddresses().toList().map {network.index to it}}
    addresses.map {(index,address)->async {
        try {
            JmDNS.create(address,"APRSERJ64").use {dns->
                dns.list(MobileRadio.kissService+"local.",2200L).flatMap {service->
                    service.inetAddresses.map {host->
                        val literal=if(host is Inet6Address) {
                            val zone=if(host.isLinkLocalAddress) "%$index" else ""
                            "[${host.hostAddress.substringBefore('%')}$zone]"
                        } else host.hostAddress
                        NetworkTnc(service.name,"$literal:${service.port}")
                    }
                }
            }
        } catch(_: java.io.IOException) {emptyList()}
    }}.awaitAll().flatten().distinct().sortedBy {it.name}
}
