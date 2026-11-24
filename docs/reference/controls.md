# Control locator

Use **Settings** for preferences and **Search → All tools…** for operating tools. Both windows have grouped navigation and a **Find a section** search that also recognizes the former short section names. **Radio & GPS ports…** and **Packet monitor…** are in Menu. Settings changes require **Save**; port changes require **Apply**.

Pause the pointer over buttons, fields, indicators, navigation entries or menu choices for hover help. Disabled controls also explain their requirements. Settings tips include the corresponding `settings.toml` key. APRS-IS verification, local packet output and remote message acknowledgment are different states; their indicators explain what has actually been confirmed.

| Location | Tasks |
| --- | --- |
| Click your callsign at the top left, or Search → My callsign & passcode | Edit callsign-SSID, passcode and APRS-IS server |
| Settings → My position, Beaconing, Station status | [Position and beaconing](../operate/beacons.md) |
| Settings → Objects & items | [Objects, items and companion stations](../operate/objects.md) |
| Settings → Map appearance, Service addresses, Station display, Tracking windows | [Maps and tracking windows](../operate/maps.md) |
| Settings → Receive area & filters, Connection recovery | [Receive filters](filters.md) and connection preferences |
| Settings → Messages, Group messages, Unattended operation, Bulletins | [Chats](../operate/messages.md), [group services](../operate/services.md) |
| Settings → History storage, Track logging, GPX imports | [Recorded tracks and GPX](../tools/tracks.md) |
| Settings → Weather alerts, APRS weather, My weather station, Notifications | [Weather inputs and boundaries](../tools/weather.md) |
| Settings → Telemetry | [Local telemetry](../tools/telemetry.md) |
| Settings → Tactical overlays, QRU server, Query replies | [QRU objects](../operate/objects.md) and [query responses](queries.md) |
| Settings → Offline maps, Satellites, Device identification | [Offline maps](../operate/maps.md), [satellites](../tools/satellites.md), platform preferences |
| Settings → Digital-mode imports | [Digital-mode log imports](../tools/digital.md) |
| Ports | [Device setup](../connect/ports.md), [wiring and gating](../connect/routing.md) |
| Packets | Packet diagnostics and filtering |
| Tools → Selected station, Weather reports, Telemetry charts | Inspect received station information |
| Tools → Export a track, Import GPX, Download maps | Exports, map layers and tile downloads |
| Tools → Group messages, Find nearby objects, Bulletins, Send APRS email | Group subscriptions, nearby objects and APRS messaging services |
| Tools → Notifications, Radio reach, Direction finding | Alert records, heard-distance and direction information |
| Tools → Repeater objects, Device identification | Repeater objects and platform information |
| Tools → Satellite passes, GPS reception | Orbital predictions and receiver sky information |
| Tools → Local sensors, Weather boundaries | Read input files and manage boundary data |

For exact configuration keys and defaults see the [settings catalog](settings.md). A toolbar action is not necessarily a saved preference.

Settings, Menu and Tools search the same combined index. Try **my callsign**, **my call**, **callsign**, **call**, **passcode**, or your configured callsign to find **My callsign & passcode**. Results are labeled by destination, and searches also include individual setting names. Opening another editor keeps an existing Settings draft open; Save or Cancel it when finished.
