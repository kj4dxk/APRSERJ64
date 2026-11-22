# APRSERJ64 user manual

**For APRSERJ64 0.8.0 on Windows x64.** Start with one working connection, then add the features you need. This manual describes APRSERJ64's controls and behavior; old APRSIS32 menu names are mapped in the [migration guide](start/from-aprsis32.md).

## Start here

1. [Install and make your first connection](start/quick-start.md).
2. [Learn the main window](start/workspace.md).
3. [Find a station and exchange messages](operate/messages.md).
4. [Set your position and beacon behavior](operate/beacons.md).
5. Add a [GPS or radio port](connect/ports.md) when needed.

## Find a task

| I want to… | Go to |
| --- | --- |
| Receive stations but not transmit | [Receive-only setup](start/quick-start.md#receive-only-use) |
| Change map tiles or download an area | [Maps and offline use](operate/maps.md) |
| Connect a USB/Bluetooth GPS | [GPS and position](operate/beacons.md#use-a-gps) |
| Send a message or check a missing ACK | [Messages](operate/messages.md) |
| Configure KISS, AGW or a Kenwood port | [Ports](connect/ports.md) |
| Wire an IGate or digipeater | [Routing](connect/routing.md) |
| Create a location, repeater or QRU object | [Objects and QRU](operate/objects.md) |
| Read or transmit weather and telemetry | [Weather](tools/weather.md), [telemetry](tools/telemetry.md) |
| Import FT8, JS8Call or JT65 logs | [Digital-mode logs](tools/digital.md) |
| Track a satellite or inspect GPS reception | [Satellites and GPS sky](tools/satellites.md) |
| Edit TOML, copy settings or update the EXE | [Files and configuration](reference/files.md) |
| Understand a setting or packet query | [Settings catalog](reference/settings.md), [query reference](reference/queries.md) |
| Diagnose a problem | [Troubleshooting](help/troubleshooting.md) |

## Search and navigation

The built web manual has full-text search. Search a task, exact label, callsign protocol, or setting key: `NO_ACK`, `KISS`, `wxnow`, `digital.ft8_file`, or `beacon.enabled`. Each page has a contents list; the sidebar groups related tasks. On GitHub, use the chapter links or repository search. The single-file offline edition supports its own search and **Ctrl+F**, without a server.

For an alphabetical entry point, use the [topic index and glossary](reference/index.md). For screen labels, use the [control locator](reference/controls.md). For longer reading or printing, use the offline edition described in [building the manual](reference/maintaining.md).

## What to expect from this version

Mapping, persistent chats, beaconing, ports, routing and the documented tools have implementations. Version 0.8.0 passed 116 automated tests. Those checks do not certify every physical radio, external gateway or GUI workflow. [Compatibility and limits](reference/compatibility.md) identifies the boundaries so a visible control is not mistaken for a tested hardware integration.

The APRSISCE/32 manual maintained in [kj4dxk/APRSIS32](https://github.com/kj4dxk/APRSIS32) informed the topic coverage. Procedures here were rewritten for APRSERJ64. See [sources and credits](reference/credits.md).
