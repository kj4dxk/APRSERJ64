# Topic index and glossary

| Topic | Read |
| --- | --- |
| ACK, chat history, retries | [Messages](../operate/messages.md) |
| ANSRVR, bulletins, email | [Messaging services](../operate/services.md) |
| APRS-IS, callsign, passcode, SSID | [Quick start](../start/quick-start.md) |
| Beacon, GPS, GeniusBeaconing, SmartBeaconing | [Beaconing](../operate/beacons.md) |
| Configuration, TOML, backup, multiple instances | [Files](files.md), [settings](settings.md) |
| CWOP, local server, UDP | [Local services](../connect/local-services.md) |
| Digipeater, IGate, RF paths | [Routing](../connect/routing.md) |
| Digital modes, FT8, JS8Call, JT65 | [Digital imports](../tools/digital.md) |
| Filters, radius, queries | [Filters](filters.md), [queries](queries.md) |
| GPX, track export, history | [Tracks](../tools/tracks.md) |
| KISS, NMEA, AGW, TNC | [Ports](../connect/ports.md) |
| Map tiles, offline, overlays, tracking | [Maps](../operate/maps.md) |
| Objects, items, QRU, frequency objects | [Objects](../operate/objects.md) |
| Satellite, TLE, GPS sky | [Satellites](../tools/satellites.md) |
| Telemetry, equations, digital bits | [Telemetry](../tools/telemetry.md) |
| Weather, wxnow, boundaries | [Weather](../tools/weather.md) |

## Terms

**ACK:** acknowledgement of an APRS message; distinct from locally sending a packet.

**APRS-IS:** Internet distribution network for APRS packets. **RF:** radio-frequency communication.

**SSID:** suffix after a callsign, such as `-13`, identifying a station instance.

**TNC:** terminal node controller connecting packet software to a radio. **KISS:** framed host/TNC protocol. **AGW:** network interface used by compatible packet engines.

**NMEA:** sentence format commonly emitted by GPS receivers. **GPSd:** service that distributes receiver information to clients. **DOP:** dilution of precision, a satellite-geometry measure.

**IGate:** gateway between RF and APRS-IS. **Digipeater:** station that repeats eligible packets on RF according to path rules.

**TNC2:** human-readable packet representation such as `SOURCE>DEST,PATH:payload`.

**DAO:** APRS position precision/datum extension. **PHG:** power, height and antenna-gain information.

**QRU:** APRS object query service for nearby information. **CWOP:** Citizen Weather Observer Program.

**TLE:** two-line orbital element set used for satellite predictions. **UGC:** geographic code used in weather alerts.

**TOML:** human-readable configuration format with named tables and typed values.
