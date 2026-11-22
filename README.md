# APRSIS64

Portable Windows APRS mapping, conversations, beaconing, radio ports and operating tools.

**Android user? [Get APRSISOSM and its APK](https://github.com/kj4dxk/APRSISOSM).** Prefer the classic Windows interface? [APRSIS32 downloads and manual](https://github.com/kj4dxk/APRSIS32).

## Get online

1. **[Download APRSIS64.exe](https://github.com/kj4dxk/APRSIS64/releases/latest/download/APRSIS64.exe)** ([release notes](https://github.com/kj4dxk/APRSIS64/releases)). Put it in a writable folder and run it; no installer or separate Java is needed.
2. Open **Settings → My position** and enter your actual fixed location or configure your GPS. Automatic beaconing defaults on: set your position before connecting.
3. Click **your callsign at the top left**, enter **your own callsign-SSID** and APRS-IS passcode, select a server, and check the receive-area coordinates. No operator identity is preinstalled.
4. Select **Save & connect**. **APRS-IS connected and verified** means the server permits transmission. Incoming stations should appear; click one to open its chat.

Use your existing callsign's APRS-IS passcode if you have one. For requests, follow the [APRSIS32 passcode instructions](https://github.com/kj4dxk/APRSIS32/blob/main/docs/manual/doc-passcode.md): email the group owners with your licensed name and callsign, explaining which application you use. The legacy service specifies APRSISCE/32 users; do not claim to use a different application. Passcode `-1` permits receive-only access.

Your callsign and passcode are stored only in your local `settings.toml`. Keep that file and your data/logs private. Never distribute your configured folder as a clean download. [Full getting-started instructions](docs/start/quick-start.md).

## User manual

## Maps and fallback

The default OSM-derived tiles come from `http://pdeffenb.homeip.net:82/raster/light/{z}/{x}/{y}.png`, independently hosted by the maintainer. This reduces requests to the OpenStreetMap community tile server. If the project server has problems, open **Settings → Map → OpenStreetMap**, then **Save**. Select **pdeffenb** there to switch back. These preset addresses are editable in your local `settings.toml`.

Changing tiles does not change routing or search services. Use the community preset for normal interactive viewing, not bulk offline downloads. Map data © OpenStreetMap contributors. This is actively developed software: test the workflows and hardware you depend on before replacing an established installation, and [report problems](https://github.com/kj4dxk/APRSIS64/issues) with the version and reproduction steps.

## User manual

**[Open the APRSIS64 manual](docs/index.md)** — organized by task, with a quick start, operating guides, device setup, troubleshooting, a topic index and searchable configuration references.

- [First connection](docs/start/quick-start.md)
- [Moving from APRSIS32](docs/start/from-aprsis32.md)
- [Settings catalog](docs/reference/settings.md)
- [Build the searchable web and offline editions](docs/reference/maintaining.md)

The manual follows the current source and calls out differences and limitations. Releases use Lynn's `YYYY.MM.DD.HHmm` version convention, recorded in [version.txt](version.txt), with matching GitHub tags. See [versioning and release procedure](docs/reference/versioning.md). Documentation builds are available as GitHub Actions artifacts after the manual workflow runs.

## Development and licensing

See [README.txt](README.txt) for build instructions and historical implementation notes, and the release reports for version-specific validation. Application source is AGPL-3.0-only; the manual text is CC BY-SA 3.0 with [sources and attribution](docs/reference/credits.md).
