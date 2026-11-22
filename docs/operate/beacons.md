# Position, GPS and beaconing

A beacon publishes your selected current position. Its position does not come from where you last dragged the map.

## Choose a fixed position

1. Open **Settings → My position**.
2. Select **Fixed location** and enter coordinates, a Google Maps coordinate link or a Plus Code in the single location box. Check the decoded latitude/longitude preview before saving. [Accepted formats](maps.md#accepted-location-formats) also include degrees/minutes/seconds and compact APRS coordinates.
3. Set altitude in meters if you use it.
4. Save and check the beacon status before transmitting.

The default fixed coordinate is a seeded example location, not a location service. Connection/filter coordinates and the map's initial center do not replace this fixed beacon coordinate.

## Use a GPS

The recommended device workflow is a named NMEA-capable port:

1. In **Menu → Radio & GPS ports…**, add an NMEA port and select the COM device/baud rate or TCP endpoint.
2. Apply the port configuration and check that it is connected.
3. In **Settings → My position**, select source `port` and select the named port.
4. Save. Wait for a valid current fix, not just an open port.
5. Inspect **Tools → GPS reception** for available satellite diagnostics.

Direct `gps_serial`, `gps_tcp`, and `gpsd` sources also exist. Do not open the same serial receiver both directly and through a named port. USB/Bluetooth devices must expose a usable Windows COM port for serial operation.

GPS freshness and accuracy limits default to 30 seconds and 100 meters. A stale or invalid fix must not be treated as a current mobile location. The status area explains the current source/state.

## Choose how often to beacon

Open **Settings → Beaconing**. The adaptive policy can react to time, start/stop transitions, heading changes, distance and forecast error. **Use time only** selects interval-driven behavior. **Use speed-based SmartBeaconing** enables the separate speed/turn settings.

Forecast error compares the current position with where the last reported speed and course would predict it. This concept follows the GeniusBeaconing™ work described by Lynn W. Deffenbaugh, KJ4ERJ; APRSERJ64's UI and implementation are its own. Do not transfer old XML timing values without checking the units here.

Use the field units: seconds, meters, degrees and knots. Minimum intervals and per-port radio limits still constrain automatic output. Reconnect/GPS-change preferences are explicit; disconnected position reports are not stored for later replay.

## Choose outputs and report format

**Beacon to APRS-IS** requires a verified IS connection. **Beacon to RF ports** requires eligible enabled transmitting ports with beacon output allowed. These switches do not enable digipeating.

Choose a symbol table/code, comments and timestamp. Compressed and Mic-E reports are available; DAO precision and ambiguity settings change coordinate representation. Use normal uncompressed reports while diagnosing format interoperability. Precision adds coordinate detail; it cannot improve the GPS measurement itself.

## Status and odometer

**Settings → Station status** controls periodic status text, interval, timestamp/grid representation. **Settings → Beaconing** also exposes odometer-related fields through the preference schema where applicable; consult the searchable [settings catalog](../reference/settings.md) for `odometer` keys. Exit odometer reporting is optional.

Select **Beacon now** on the toolbar for a manual station beacon request. Review the timestamp, reason and **Menu → Packet monitor…** view. To stop or limit output, see [routing controls](../connect/routing.md#stop-or-limit-transmissions).
