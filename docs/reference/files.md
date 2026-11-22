# Files, configuration, backup and updates

## Portable folder layout

| Path beside the EXE | Contents |
| --- | --- |
| `settings.toml` | Account/passcode, endpoints, presets, maps, features, objects and ports |
| `data/chats.sqlite` | Conversations and outgoing message state |
| `data/stations.sqlite` | Recorded station/track, weather and telemetry information |
| `data/` | Additional journals, imported boundary files and feature state |
| `cache/tiles.db` | Map engine's normal resource cache |
| `cache/offline/` | Explicitly downloaded areas, separated by provider |
| `exports/` | Requested track exports |
| `logs/` | Launch/packet/track diagnostics, according to enabled features |
| `app/<payload-id>/` | Extracted runtime and application dependencies |

Some files appear only after the corresponding feature runs. The launcher works from the EXE's folder even when a shortcut has another working directory.

## Edit settings.toml

Close the program first, back up the file, and edit it as UTF-8 plain text. Settings saved in the app can replace concurrent external edits; comments/formatting are not a persistent preference mechanism.

```toml
[aprs]
callsign = "N0CALL-13"  # replace with your own call
passcode = "-1"        # receive-only example
server = "rotate.aprs2.net"
port = 14580
connect_on_startup = true
```

This is a fragment, not a replacement for an existing complete file. Edit an existing table in place. A TOML table such as `[aprs]` must not be declared twice. Strings use quotes, booleans are lowercase `true`/`false`, and numbers are unquoted. Arrays use brackets; indentation is optional.

For a Windows path use a literal single-quoted string such as `'C:\Radio\ALL.TXT'`, or escape backslashes in a double-quoted string. Avoid curly “smart quotes.” Preserve complete server/port/object arrays when editing them. The [settings catalog](settings.md), [port fields](ports.md) and [object fields](objects.md) are searchable references.

## Credentials and backups

The passcode is readable in the settings file. Do not include it in a public issue, Git commit or shared diagnostic bundle. Logs and exported tracks can contain locations and message content.

Close the application and copy the **whole portable folder** for a straightforward backup. This avoids copying live SQLite files inconsistently. Copying just the EXE gives you the program, not its chats/settings.

## Update or run another instance

Close the program and replace its EXE with the new portable build. It extracts a new runtime payload while retaining settings and user data. Keep the previous EXE/folder backup until satisfied with the update.

For a second simultaneous instance, use a separate folder and suitable account/SSID/device assignments. The launcher locks `data/running.lock` within one folder to protect its data; it does not globally prohibit multiple installations.

If settings fail to load, the program does not reset them automatically. Follow [settings recovery](../help/troubleshooting.md#settings-will-not-load).
