# Tracks, GPX import and exports

## Record and export received tracks

Enable history in **Settings → History** and choose storage/track limits. In **Tools → Tracks**, enter the station or object name and select **Export GPX** or **Export CSV**. The result shows the saved path. Exports are placed in the portable installation's exports area.

A track contains observations actually retained by the app. Missing reception or history limits leave gaps; a drawn line between points is not proof of travel along that exact route. Sending the latest recorded position in a message sends history, not necessarily the station's current location.

## Import GPX files

1. Place a GPX file in a stable location, preferably inside the portable folder.
2. Open **Tools → GPX import** and enter its path.
3. Select **Add file** and check the result.
4. Use **Reload files** after updating a source file, or **Remove layer** to stop displaying it.

Tracks, routes and named waypoints are supported. The import point/file limits keep very large files from overwhelming the display. A relative path is resolved from the application folder. Imported waypoints are map content, not necessarily APRS stations you can message.

## Automatic GPX and CSV logging

Use **Settings → Logging** to enable automatic track files, select calls, and set retention. The generated files live under `logs/tracks/`. Retention applies to those automatic files; do not assume it deletes exported files or all database history.

## Replay and investigation

Version 0.8.0 provides saved-track exports, not an interactive replay timeline. Use GPX/CSV exports for historical investigation; saved observations are not live reports. For packet-level diagnosis use [Packets](../help/troubleshooting.md#collect-a-useful-diagnostic-record), which preserves provenance that a GPX track does not.

See also: [portable files and backups](../reference/files.md), [map display](../operate/maps.md).
