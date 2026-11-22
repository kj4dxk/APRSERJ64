# Moving from APRSIS32

Use the old manual for concepts and the table below to locate the APRSIS64 workflow. Do not assume an old menu, keyboard shortcut or XML tag has the same implementation here.

| APRSIS32 topic | APRSIS64 location/change |
| --- | --- |
| Initial startup, callsign and passcode | [Connection and quick start](quick-start.md) |
| Configure / General, map settings | [Settings and map chapter](../operate/maps.md) |
| Set Location, GPS, GeniusBeaconing™ | [Position and beaconing](../operate/beacons.md) |
| Port Configuration, Open/CloseCmds | [Ports workspace](../connect/ports.md) |
| Enables / Ports, DigiXform, IGate | [Per-port routing and rules](../connect/routing.md) |
| Messages / Chat / Pending Messages | [Persistent conversation panel](../operate/messages.md) |
| Announcements / ANSRVR | [Tools → Groups](../operate/services.md) |
| Configure Objects / Multiline / QRU | [Objects and QRU](../operate/objects.md) |
| Tile Sets / Map Prefetch / MultiTrack | [Maps and offline use](../operate/maps.md) |
| Save Posits / Tracks | [Tracks and GPX](../tools/tracks.md) |
| Weather Stations / Tempest / NWS | [Weather bridge and boundaries](../tools/weather.md) |
| Telemetry / TT4 Telemetry | [Telemetry](../tools/telemetry.md) |
| Satellite Tracking / GPS sky | [Satellite and GPS diagnostics](../tools/satellites.md) |
| Digital log-driven stations | [FT8, JS8Call and JT65](../tools/digital.md) |
| XML configuration editing | [Portable TOML](../reference/files.md) |

## Migrate deliberately

1. Keep the old installation intact while setting up a separate APRSIS64 folder.
2. Enter the account, actual position, map source and connection information first.
3. Recreate one device port at a time and verify reception before enabling forwarding.
4. Recreate objects and scheduled services with explicit output choices.
5. Compare actual packets and replies, not only how similar the screen looks.

There is no general APRSIS32 XML import tool. Do not rename the old XML to TOML or paste XML blocks into it. The APRSIS64 settings loader can migrate its own older `settings.properties` format when appropriate; that is a different format from APRSIS32 XML.

## Important differences

APRSIS64 uses a portable EXE/runtime folder, TOML preferences and SQLite history. Multiple copies can run from separate folders; the launcher protects one folder from simultaneous use. North-up is fixed. Message retries and route choices differ from the historical manual. Query-only objects use an explicit setting, not the old zero-interval recipe. Satellite, weather and device support have the limits listed in [compatibility](../reference/compatibility.md).
