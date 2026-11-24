# Local feeds, servers, UDP and CWOP

## Choose the right service

| Service | Typical role |
| --- | --- |
| Operating receive-only feed | Share received TNC2 packets with another local application; default port 14581 |
| IS-Server | APRS-style client login/filter server; messages are delivered independently of subscriptions |
| Local-Server | Local packet feed/server with an unfiltered-feed default |
| UDP-Listen | Receive/send permitted local UDP packet traffic |
| Syslog-UDP | Packet traffic wrapped for a syslog-style UDP workflow |
| Dummy | Accept output without a physical radio; useful for isolated checks |
| CWOP | Send locally produced weather to the configured CWOP endpoint |

## Share a receive-only feed

Open **Settings → Receive area & filters**, enable **Share received packets with local apps**, and select an unused local port. This feed does not provide an alternate radio transmitter. Configure the consuming application for the corresponding local TCP endpoint.

## Set up a local server port

1. Add an **IS-Server** or **Local-Server** port through **Configure → Radio & GPS ports…**.
2. Set the bind endpoint; a loopback address limits access to this PC.
3. Decide whether to enable **Accept local service input**. It defaults off.
4. Configure forwarding separately: accepting a local client's packet does not by itself enable RF or IS output.
5. Apply, then connect the client with its APRS login and filter.

Verified clients can submit eligible gated stations. Application replies preserve a learned local return route. Addressed messages and associated positions bypass subscription exclusions. Heartbeat comments keep idle clients informed. This is a local service implementation, not a claim to replace an APRS-IS backbone router.

## UDP and syslog

Local UDP accepts loopback senders or configured source IPs. List allowed peers explicitly when using another machine. Select the actual protocol, endpoint and forwarding switches; a TCP connection setting shown in a generic port form is not a statement that a UDP service uses TCP on the wire.

## CWOP weather output

CWOP uses its configured station identity and endpoint and accepts weather output, not arbitrary chats or beacons. Configure and validate the [local weather input](../tools/weather.md#transmit-local-weather) first. Received remote weather should not be relabeled as your own observation. A locally accepted write is not confirmation that CWOP ingested the observation.

See also: [ports](ports.md), [routing](routing.md), [service compatibility limits](../reference/compatibility.md).
