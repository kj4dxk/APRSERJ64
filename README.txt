APRSIS64 — Windows map and chat prototype

Source: https://github.com/kj4dxk/APRSIS64
Shared core: https://github.com/kj4dxk/aprs-core (pinned git submodule)

License: AGPL-3.0, matching APRSFoundation/APRSISMO. No warranty.
Copyright 2026 APRSIS64 contributors. See LICENSE and THIRD-PARTY.txt.
Extracted GPLv3 core components retain their original terms and notices.
No APRSIS32 proprietary source or OsmAnd artwork is included.

WHAT WORKS
- Native MapLibre map using pdeffenb raster tiles; north-up with rotation disabled.
- APRS-IS connection with callsign/SSID, passcode and editable host:port.
- Stations on the map; click a marker or station list entry to open its conversation.
- Local SQLite chat history, full-SSID conversations, unread indicators, durable
  outgoing queue, ACK/REJ/reply-ACK processing, retry limits and duplicate handling.
- Local demo uses the real shared APRS-IS transport against a loopback-only server.
  It never connects to the public APRS network. Demo data uses N0CALL-12.
- New incoming messages are saved before ACK. Passcodes are never saved to disk.

LIMITS OF THIS PREVIEW
This is not full APRSIS32 parity. Markers are circles, not the complete APRS symbol
atlas. No Mic-E/ambiguous positions, RF, beacon transmission, GPS, routing UI,
objects editor, weather/telemetry, satellite passes or offline map manager yet.
The 100 km feed filter is centered at the configured coordinates; panning the map
does not change it. Chats load complete history; pagination remains future work.
Queued messages expire after 15 minutes and stop after five attempts, separated
by at least 30 seconds. ACK means packet receipt, not that the recipient read it.
Saved chats survive app restarts; settings contain no passcode. There is no cloud
backup or analytics. Live sending requires a verified login and an explicit Send.

RUN
Open APRSIS64.exe from the complete packaged APRSIS64 folder, or install the MSI.
The Java runtime is bundled. Keep the app/ and runtime/ folders beside the EXE.
Choose Local demo to test without a callsign. Choose Connection for real APRS-IS.
Default server: rotate.aprs2.net:14580; blank passcode means receive-only.
Default map: http://pdeffenb.homeip.net:82/raster/light/{z}/{x}/{y}.png
Map availability depends on that service; APRS and chat continue independently.
Normal data: %LOCALAPPDATA%/APRSIS64/chats.sqlite and settings.properties.

BUILD
git clone --recurse-submodules https://github.com/kj4dxk/APRSIS64.git
Install JDK 25 and JDK 17. Java 25 is required by the native MapLibre integration;
the shared library is tested separately on Java 17 for Android compatibility.
Set JAVA_HOME to JDK 25 and JAVA17_HOME to JDK 17, then:
  .\gradlew.bat test createDistributable
  .\gradlew.bat packageMsi
Output: build/compose/binaries/main/app/APRSIS64 and main/msi.
Gradle wrapper, Kotlin, Compose and MapLibre versions are pinned in source.
No Android SDK or Android Studio is required to build the Windows application.

ANDROID SHARING
APRSISOSM includes the same aprs-core submodule's common sources in OsmAnd-shared.
Android keeps its Java 17/Kotlin toolchain and existing foreground service/UI.
Windows uses the new shared ChatSession and a JDBC ChatRepository adapter.
SQLDelight schema convergence and Android chat-policy migration are later work;
the prototype deliberately does not risk an existing phone's chat database.

DIAGNOSTICS
Launch with --demo to use only the local fake APRS server. Optional JVM properties:
  aprsis64.dataDir — alternate data directory for isolated tests
  aprsis64.probe=true — log native rendered-feature hit test after startup
These properties do not enable live transmission or store credentials.
The demo-only 10,000-station button is a renderer stress tool, not live data.

GRAPHICS
MapLibre Vulkan producer feeds Compose's Direct3D12 presentation on Windows.
Intel Iris Xe testing rejected OpenGL/Direct3D texture import (GL error 0x500),
so the packaged default uses Vulkan. See TEST-REPORT.txt for measured results.
This is a prototype integration, not a guarantee across all Windows drivers.
