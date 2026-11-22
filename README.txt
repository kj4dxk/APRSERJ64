CURRENT: APRSIS64 0.8.0. See RELEASE-0.8.0.txt for changes and validation.
User manual: docs/index.md. Searchable/offline editions: docs/reference/maintaining.md.

APRSIS64 — portable Windows APRS map, chat and beaconing

RELEASE 0.6.0: the twelve reviewed gap areas are implemented. 99 automated
tests pass, with packaged Windows visual/restart QA. See RELEASE-0.6.0.txt for
features, settings, validation and remaining hardware/interoperability limits.
The release report supersedes the historical implementation notes below.

TACTICAL UPDATE: ten additional APRSIS32-inspired capabilities compile and package;
82 tests pass (49 shared core, 33 Windows). Portable native-map startup/probe passed.
The ten new capabilities received interactive Windows visual QA with local APRS, RF
and weather fixtures. See TACTICAL-VISUAL-TEST-REPORT.txt for coverage and fixes.
Physical radio and third-party interoperability testing remain outstanding.

RELEASE 0.5.0: Windows/shared-core build and 70 tests pass. Visual QA covered
all settings categories and operating-tools tabs, map, chat, persistence and
representative port workflows. Physical radio testing remains outstanding.
See VISUAL-TEST-REPORT.txt and TEST-REPORT.txt for coverage and limitations.

Source: https://github.com/kj4dxk/APRSIS64
Shared core: https://github.com/kj4dxk/aprs-core (pinned git submodule)
License: AGPL-3.0, matching APRSFoundation/APRSISMO. No warranty.
Extracted GPLv3 components retain their original terms and notices.
See LICENSE and THIRD-PARTY.txt. No proprietary APRSIS32 source is included.

RUN
Download APRSIS64.exe, put it in a writable folder, and run it. No installer,
administrator access, or separate Java installation is needed on Windows x64.
The bootstrap uses Windows' built-in .NET Framework; the Java runtime and all
application dependencies are embedded in the EXE and extracted on first launch.
It always works relative to the EXE, even if a shortcut starts elsewhere.

The program creates these items beside APRSIS64.exe:
  settings.toml         Account, server list, maps, services, GPS and beacon settings
  data/chats.sqlite     Persistent chat history and outgoing queue
  data/stations.sqlite  Track, weather and telemetry history
  cache/offline/        Explicitly prefetched base tiles by provider
  exports/              GPX tracks and CSV position exports
  cache/tiles.db        Downloaded map resources (512 MiB ambient cache limit)
  cache/tmp/            Native-library extraction and temporary runtime files
  logs/                 Current and previous launch diagnostics
  app/<payload-id>/     Bundled application and Java runtime

Close APRSIS64 before moving its folder. Move the whole folder to retain settings,
chats and cached tiles. Moving just the EXE starts a fresh copy. An updated EXE
unpacks a separate runtime subfolder without replacing settings, chats or tiles.
Only one instance can run from a folder. Data does not go to AppData or the registry.
Old app/<payload-id> runtime folders may be removed manually when no longer needed.


SETTINGS FORMAT
settings.toml is UTF-8 TOML. See settings.example.toml for the complete schema. Strings use quotes,
numbers do not, and booleans are true or false. Indentation is optional.
Example (no personal credentials):

[aprs]
callsign = "N0CALL-13"
passcode = ""
server = "rotate.aprs2.net"
port = 14580
connect_on_startup = true

[map]
latitude = 28.536725
longitude = -81.242932

If settings.toml is absent, the next TOML-capable build imports settings.properties
and keeps the original file intact. Once TOML exists, it takes precedence. Invalid
TOML is rejected rather than overwritten with defaults. Close the app before hand
editing settings. Application saves rewrite the known settings in the format above;
custom comments and unknown fields are not retained when saving.
FIRST CONNECTION
Open Connection, enter your callsign including SSID and APRS-IS passcode, choose
a preset or Custom server, then Connect. Passcode is saved in settings.toml.
Connect on startup is enabled by default; Disconnect disables automatic startup
connection until enabled again. The distributed EXE contains no personal credentials.
Local settings, data and caches are excluded from the source repository.

The initial 12 presets and order come from APRSISOSM's aprs_servers.json.
The runtime menu reads [[aprs.servers]] in settings.toml. Add, remove or reorder
those blocks and restart; edits are preserved, including an empty server list.
[aprs].server and port select the active endpoint independently of the menu.
Initial presets:
rotate.aprs2.net (default), noam.aprs2.net, euro.aprs2.net, asia.aprs2.net,
aunz.aprs2.net, soam.aprs2.net, rotate.aprs.net, germany.aprs2.net,
japan.aprs2.net, nordic.aprs2.net, swiss.aprs2.net, china.aprs2.net.
All use port 14580; Custom accepts host:port or tcp://host:port.
This is the existing practical priority order, not an audited popularity ranking.
A blank passcode is receive-only. Live message transmission requires verified login.

MAP AND CHAT
Map tiles download automatically for the visible area from:
http://pdeffenb.homeip.net:82/raster/light/{z}/{x}/{y}.png
The map stays north-up. The initial view and 100 km APRS feed filter are centered
near 32825; filter coordinates are editable in Connection. Panning does not change
the APRS filter. Map availability depends on pdeffenb; APRS/chat work independently.
MapLibre's optional logo is removed. A small clickable OpenStreetMap credit remains.

Click a station marker or station list entry to open its conversation. The Messages
panel holds conversations by complete callsign/SSID. History, unread state and the
outgoing queue persist. Incoming messages are saved before ACK; duplicates are
suppressed. Retries stop after five attempts at least 30 seconds apart or 15 minutes.
ACK is delivery acknowledgement, not a read receipt. APRS messages are public.
The Local demo button or --demo argument uses a loopback-only test server.

CURRENT SCOPE
Source adds adaptive and fixed beaconing, serial/TCP/GPSd NMEA positions, status,
objects/items and companion station reports. See BEACONING.txt for coverage and
limitations. Named RF/GPS ports, digipeating and IGating are described in RADIO-PORTS.txt.
This is not full APRSIS32 parity. Markers are circles. No routing
UI, weather/telemetry integrations, satellite passes or offline download manager. Cached tiles
are reused as MapLibre's HTTP cache permits; there is no bulk tile pre-download.
Chats currently load complete history rather than pages.

BUILD
Clone with --recurse-submodules. Install JDK 25 and JDK 17; set JAVA_HOME to 25 and
JAVA17_HOME to 17. Windows .NET Framework's C# compiler builds the bootstrap.
  .\gradlew.bat test packagePortable
Output: build/portable/APRSIS64.exe (single file, approximately 93 MiB).
Do not distribute the internal createDistributable EXE alone: it needs its runtime.
The top-level portable EXE contains that runtime and creates the required folders.
The GitHub Actions workflow builds the portable EXE, not an MSI.

ANDROID COMPANION
The current APRSISOSM Android application is standalone and implements its own
map, routing and APRS logic. It follows the Windows station-to-chat workflow,
full-SSID conversation identity and durable outbox approach. Windows continues
to use the aprs-core submodule with a JDBC chat adapter. See the Android repository
for its current feature set, regional map requirements and operating limitations.

GRAPHICS AND DIAGNOSTICS
MapLibre Vulkan feeds Compose Direct3D12 on Windows. The Intel Iris Xe driver on the
test PC rejected OpenGL texture import, so Vulkan is the default. Native libraries'
license notices are bundled. See TEST-REPORT.txt for test evidence and limitations.
For source development, -Daprsis64.home can set a test folder; the portable launcher
sets APRSIS64_HOME automatically. -Daprsis64.probe=true enables a rendered-feature
query diagnostic in development launches. Diagnostic output never prints passcodes.

CONFIGURABLE SERVICES
[map].tile_url defaults to the APRSISOSM pdeffenb raster/light template. Online
maps are on; overlay and underlay are off. Their URLs, opacity, tile size, zoom
limits, initial zoom, cache budget, frame limit and attribution are configurable.
North-up is enforced without a setting, as requested. There are no cloud backup,
analytics, paid-feature links, weather or Wikipedia modules in this Windows app.
[services] contains the pdeffenb base, routing, search, map page, setup and monitor
URLs, plus the source/license link. Routing/search are reserved configuration for
future features, not implemented navigation or search. Each URL is independent;
changing base_url alone does not rewrite the other URLs.
Resources seed missing settings only. Runtime map/connection UI reads the user's
settings. Existing values and preset lists survive upgrades that fill new keys.
Close the app before editing TOML. Cache budget changes require restart.
[position] contains GPS endpoints and fixed station coordinates separately from
[map]'s initial view/feed center. Confirm the fixed location for your station.
[beacon].enabled defaults true; a verified login and usable position are required.
RADIO PORTS
The main Ports button opens the visual workspace for serial/TCP NMEA, KISS, AGW and text/command
TNC profiles. All per-port options live in [[ports]] in settings.toml. Static
objects can target APRS-IS, selected RF ports, or both. See RADIO-PORTS.txt for
profile coverage, defaults, setup, routing behavior and pending validation.

VISUAL PORT WORKSPACE
The Ports button opens an expansive graph with live status, an inspector, a
guided setup flow, object route matrix and Radio limits. Click a source port
and its destination controls to rewire; digipeater outputs can be selected on
the diagram. Changes are staged with Undo/Redo until Apply changes. See
PORT-WORKSPACE.txt for behavior and the APRSIS32/defaults comparison.
The workspace compiles and its transaction tests pass; full visual interaction review remains outstanding.

TOP 15 FEATURE COVERAGE
See TOP-15-FEATURES.txt for each ranked category, setup instructions, evidence
and explicit limits. New controls: Ports > Add port presets; Settings > Operating
for server filters/local feed; Settings > Beacon for SmartBeaconing; Packets for
traffic inspection, satellite filtering and export; Connection > SSID chooser.

TOP 30 FEATURE WORKFLOWS (0.5.0)
See TOP-30-FEATURES.txt for coverage and limitations of all ranked categories.
Open Tools for weather, telemetry graphs, track exports, offline tile downloads,
ANSRVR groups, QRU queries, bulletins and activity alerts. Settings contains their
editable defaults, URLs, retention limits, notification and service controls.
Use station filters and Save view defaults to retain filters and tracking calls.
Up to eight separate tracking windows can follow stations; maps remain north-up.
Multiple program instances are supported in separate writable folders.
Weather alerts, radar, sounds, desktop notifications and server failover default
off. Radar requires a provider URL. Offline mode uses explicitly downloaded base
tiles and disables network overlays. Prefetch only areas your provider permits.
