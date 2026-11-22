# Troubleshooting

Start with the symptom, change one thing, and check the result in **Menu → Packet monitor…** and the relevant status area.

## Map is blank

1. Check whether stations still arrive. Map tiles and APRS-IS are separate connections.
2. Confirm **Settings → Map appearance** has the intended URL with `{z}`, `{x}`, `{y}`, a supported zoom range and online tiles enabled when needed.
3. If offline-only mode is on, check that the current area/provider was downloaded successfully.
4. Check reachability of the configured pdeffenb/custom tile service. Changing the APRS server does not repair a tile server.

## No stations appear

Open **Menu → Packet monitor…**. If RX records arrive, clear **Find station**, the Show filter and age restrictions. Check that the map is centered on the received region. If no RX records arrive, inspect connection state, endpoint, filter center/radius, custom filters and network access. Demo data is not public reception.

## Connected but not transmitting

Check for **VERIFIED**, not merely connected. Check callsign/SSID, passcode, the selected current position, feature enable switch and output route. `-1` is receive-only on IS. A stale GPS fix, disabled port output or per-port rate limit can also block a send.

**Beacon now** requests a station beacon; it does not repair every scheduled feature's configuration. For objects, check Enabled, Alive, query-only mode, interval and destinations.

## Message stays queued or NO_ACK

Inspect the saved conversation route. RF needs an eligible message-enabled radio; IS needs a verified upstream connection. BEST can retain a recently learned local-client route. A locally accepted packet is not an ACK. Check whether the recipient is online and whether an intermediate IGate can reach it. Read the [retry behavior](../operate/messages.md#delivery-states) before sending repeated new copies.

## GPS or radio port will not connect

Check COM name, baud, protocol, device mode and the TCP endpoint where applicable. Close another application that owns the same serial device. A Bluetooth receiver must expose a usable COM port. Inspect profile command responses and timeout settings; do not keep changing radio paths to fix a physical connection problem.

An open NMEA port with no valid fixes is a reception/format issue, not proof that positioning works. Use **Tools → GPS reception** and the beacon source/detail.

## A wire is visible but packets do not flow

Check whether the diagram is still an unapplied draft. Then inspect live connection status, direction switches, output eligibility, transform rules, message/beacon/object flags and rate limits. Dashed wires indicate blocked draft routes; solid wires do not prove a live remote receiver. Recheck routes after a rename or deletion.

## Settings will not load

1. Close the app and copy the failing file so you can undo your changes.
2. Compare the last edit with the [TOML rules](../reference/files.md#edit-settingstoml).
3. Look for repeated table headers, missing quotes/brackets, invalid numbers or a field with the wrong type.
4. Restore the previous known-good file if available, then reapply edits in small steps.

Do not delete `data/` to repair TOML. The application deliberately preserves the original settings on load failure. Error reports should omit the passcode.

## Weather or telemetry input is stale

Confirm the producer is writing the configured file, not a different copy. Check file age, embedded wxnow UTC time, supported format and the required current station position for weather. Validate with **Tools → Local sensors**. Do not extend age limits simply to retransmit yesterday's observations.

## Digital tab is empty or imported stations are missing

The 0.8.0 Digital tab has an incomplete field binding; use the [TOML recipe](../tools/digital.md). Confirm the log type/path, fresh receive timestamps, an explicit grid, a valid local account and the configured age/quantity limits. A Tx entry or `RR73` is not a location.

## Satellite positions or times look wrong

Check PC clock/time zone, observing location, selected satellite names, TLE file checksums and age. Reload/download the intended file. Stale data is not made accurate by reducing the pass-search interval.

## Collect a useful diagnostic record

Use **Menu → Packet monitor…** to narrow port, direction and text, pause the display if useful, and **Export TNC2**. Pausing that display does not stop radio reception. Include the app version, reproducible steps, relevant redacted settings, expected behavior and a short packet sample. Keep callsign/passcode secrets and private message/location information out of public reports as appropriate.

See [compatibility limits](../reference/compatibility.md) before treating a known unsupported behavior as a device failure.
