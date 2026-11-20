APRSIS64 — portable Windows APRS map and chat

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
  settings.properties   Callsign/SSID, passcode, server, filter and startup setting
  data/chats.sqlite     Persistent chat history and outgoing queue
  cache/tiles.db        Downloaded map resources (512 MiB ambient cache limit)
  cache/tmp/            Native-library extraction and temporary runtime files
  logs/                 Current and previous launch diagnostics
  app/<payload-id>/     Bundled application and Java runtime

Close APRSIS64 before moving its folder. Move the whole folder to retain settings,
chats and cached tiles. Moving just the EXE starts a fresh copy. An updated EXE
unpacks a separate runtime subfolder without replacing settings, chats or tiles.
Only one instance can run from a folder. Data does not go to AppData or the registry.
Old app/<payload-id> runtime folders may be removed manually when no longer needed.

FIRST CONNECTION
Open Connection, enter your callsign including SSID and APRS-IS passcode, choose
a preset or Custom server, then Connect. Passcode is saved in settings.properties.
Connect on startup is enabled by default; Disconnect disables automatic startup
connection until enabled again. The distributed EXE contains no personal credentials.
Local settings, data and caches are excluded from the source repository.

The exact 12 presets and order come from APRSISOSM's aprs_servers.json:
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
Map and chat, not full APRSIS32 parity. Markers are circles. No Mic-E/ambiguous
positions, beacon transmission, RF, GPS, routing UI, objects editor, weather,
telemetry, satellite passes or offline region download manager yet. Cached tiles
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

ANDROID SHARING
APRSISOSM includes the same core's common sources in OsmAnd-shared. Android retains
its Java 17 toolchain and existing foreground service/database. Windows uses shared
ChatSession with a JDBC adapter. Android database migration is not part of this work.

GRAPHICS AND DIAGNOSTICS
MapLibre Vulkan feeds Compose Direct3D12 on Windows. The Intel Iris Xe driver on the
test PC rejected OpenGL texture import, so Vulkan is the default. Native libraries'
license notices are bundled. See TEST-REPORT.txt for test evidence and limitations.
For source development, -Daprsis64.home can set a test folder; the portable launcher
sets APRSIS64_HOME automatically. -Daprsis64.probe=true enables a rendered-feature
query diagnostic in development launches. Diagnostic output never prints passcodes.
