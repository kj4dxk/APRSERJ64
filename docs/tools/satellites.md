# Satellites and GPS sky

## Load satellite orbital data

1. Open **Tools → Satellite passes**.
2. Use **Download TLEs**, or enter a local TLE file path and select **Load file**.
3. Review source URL, selected names, maximum age and prediction window in **Settings → Satellites**.
4. Enable **Show tracked satellites** and check the plotted positions and footprints.
5. Select **Predict passes** for rise/set and maximum elevation estimates.

TLE means two-line element set. Checksums and age limits protect against malformed or outdated input. Check the PC clock and your observing position as well: correct orbital data with the wrong ground position gives the wrong azimuth/elevation.

## Interpret a pass

Azimuth is direction around the horizon; elevation is height above it. A footprint is a geometric estimate, not proof that a satellite's radio is active or that a contact is possible.

Version 0.8.0 brackets pass events with 10-second samples and refines crossing/peak timing to approximately one-second intervals. Its Tools heading still says “30-second sampling”; that label is stale. TLE/propagation uncertainty remains larger than the numerical refinement in some cases. Displayed pass timestamps are UTC.

This workflow does not automatically tune a radio or control a rotator. Confirm operating status separately using the configured satellite-status resource.

## Inspect GPS reception

Open **Tools → GPS reception** after connecting a named NMEA-capable port. The view shows available satellites, elevation/azimuth, signal strength, used-in-fix flags and DOP information when the receiver supplies GSV/GSA sentences.

Multiple constellations and signal IDs are combined without treating the same satellite's different signals as separate satellites. A connected port with no current sentences is not a valid fix. For actual beacon eligibility, inspect the selected position source and its freshness limits.
