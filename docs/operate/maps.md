# Maps, stations and offline use

Open **Settings → Station display → Display units** to choose **Imperial** (the default) or **Metric**, then select **Save**. Weather follows this choice when **Weather units** is set to **Follow display units** in Settings → Weather alerts or in the Weather reports tool. Existing saved weather preferences are kept. Configuration fields and APRS filter expressions use the units shown in their labels and syntax.

Open **Settings → Map appearance → Tile resolution** to choose **Follow Windows** (the default), **Standard (1×)**, or **High resolution (2×)**, then select **Save**. Follow Windows requests standard pdeffenb tiles at 100% display scaling and 512-pixel tiles above 100%, including at 150% and 200%. The manual choices apply regardless of Windows display scaling. All choices show the same map extent.

The main renderer keeps cached pdeffenb tiles visible while it fetches the selected resolution. When replacement tiles arrive, they appear automatically. The app reuses matching tiles from older map caches and keeps both resolutions available for later sessions. With no internet, it can continue showing either cached resolution wherever you have coverage. **Use downloaded base map only** also allows matching cached pdeffenb tiles without contacting the server.

New profiles use the Windows app color setting. Open **Settings → Station display → Window color theme** to choose **Follow operating system** (the default), **Dark**, or **Light**. Select **Save** to apply the choice to open windows and future sessions. The map tile style is selected separately.

## Navigate without changing your beacon position

Pan and zoom the map. Use **+**, **−**, **Home**, or open the magnifying-glass **Go to coordinates** control. Paste a location in the single **Location** field, check the interpreted latitude/longitude shown below it, then select **Go**. The field stays collapsed until needed. **Follow station** is available when the selected station has a map position. North is always up; rotation has no preference. Moving the map does not move your station's configured fixed position.

### Accepted location formats

For a place name, street address or postal code, press **Enter** or select **Search places**, then select a result and check its coordinates. The configured Photon service supplies place names. Coordinate strings and Plus Codes are decoded locally.

The map requests the visible area after panning stops. Previously viewed areas remain subscribed for one hour. Overlapping areas share a filter, and date-line views use separate eastern and western areas. The configured APRS-IS filter and saved station subscriptions take priority over additional areas when server limits are reached. World-scale views omit the geographic subscription. To track a station before its position arrives, enter its callsign in **Find station** and select **Track**. Open Settings and search for **messaged callsigns** to remove a permanent receive subscription.

These formats work throughout location entry: map navigation, fixed station position, receive-area center, object and repeater locations, direction-finding origins, offline-map corners, and each line of polygon vertices. Editors show the decoded coordinates; invalid input cannot retain an earlier valid location for saving. Short Plus Codes use the editor's original location as a nearby reference (map navigation uses the map center, and polygon vertices use the object's location).

| Format | Example |
| --- | --- |
| Decimal degrees, latitude first | `28.536725, -81.242932` |
| Degrees, minutes, seconds | `28°32'12.21"N 81°14'34.56"W` |
| Degrees and decimal minutes | `28 32.20 N 81 14.58 W` |
| Compact APRS coordinates | `2832.20N/08114.58W` |
| Google Maps coordinate URL | `https://www.google.com/maps/search/?api=1&query=28.536725,-81.242932` |
| Full Open Location Code (Plus Code) | `849VCWC8+R9` |
| Short Plus Code near a reference | `CWC8+R9`, after centering near Mountain View, California |

Directions may precede or follow numbers. Extra whitespace and common punctuation are tolerated. Within numeric tokens, recognizable typing substitutions such as `O` for zero and `I` or `l` for one are corrected and flagged in the preview. The preview always shows the location that **Go** will use. Invalid ranges, unknown letters and ambiguous input leave **Go** disabled; arbitrary typos cannot be guessed reliably.

A full Plus Code identifies an area; **Go** uses its center. A shortened code uses the map center at the time you enter or edit the code as its nearby reference. The preview names that reference and the recovered full code. Center near the intended place first, or use a full code. A short code with a place name is not geocoded automatically.

Google links containing a pin, coordinate query or `@latitude,longitude` viewport are accepted. When a link contains one explicit pin, its position takes priority over the map viewport. Short sharing links such as `maps.app.goo.gl/...`, street addresses and links with multiple pins require opening Google Maps and copying the intended coordinates. Parsing runs locally and does not resolve links over the network.

Select a station to inspect it, show its trail and received path, or open its chat. Estimated positions and coverage circles are aids: a predicted position is not a newly received fix, and a PHG range circle is not a measured coverage boundary.

## Choose the tile source

Open **Settings → Map appearance**. The default raster source is `pdeffenb`; the shipped URL is:

```text
http://pdeffenb.homeip.net:82/raster/light/{z}/{x}/{y}.png
```

The braces are template placeholders and must remain intact. Set the provider's minimum/maximum zoom and attribution with its URL. Overlay and underlay default off. A working APRS-IS connection does not guarantee that the tile server is reachable.

The URLs under **Settings → Service addresses** are separate from the raster tile template. A configured routing/search service address does not imply a turn-by-turn navigation workflow. [Service settings](../reference/settings.md) lists the actual saved fields.

## Download an area for offline use

1. Open **Tools → Download maps**.
2. Enter south, west, north and east bounds in decimal degrees.
3. Choose a small zoom range; high zoom levels greatly increase the number of tiles.
4. Use **Preview**, then **Download**.
5. Wait for the result. Review any failed downloads before relying on the area.
6. Enable offline-only base-map use through the Offline controls/settings when you want the app to use the downloaded tiles.

The download budget and maximum tile count are in **Settings → Offline maps**. Explicit downloads are stored separately from the map engine's ordinary browsing cache. An area downloaded for one provider URL is not automatically available after changing that URL. The area planner does not accept a west/east box that crosses the date line; split that download into two areas.

## Tracking windows

Select a station and choose **Track selected station**. Each tracked callsign can save independent map source, initial zoom, trail and prediction preferences. **Settings → Tracking windows** exposes saved overrides. All tracking windows remain north-up.

## Paths, labels and overlays

Use **Settings → Tactical overlays** for packet paths, labels, nicknames, prediction duration, PHG, shapes and direction-finding display. A nickname is a local label; it does not change the station's on-air identity. Use **Tools → Selected station**, **Radio DX**, **Direction finding**, **Frequency** and **Platforms** to inspect received information.

See also: [GPX and tracks](../tools/tracks.md), [weather boundaries](../tools/weather.md#weather-warnings-and-boundaries), [blank-map troubleshooting](../help/troubleshooting.md#map-is-blank).
