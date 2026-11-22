# APRSERJ64 common-workflow GUI and live-feed QA

Tested on this Windows PC on November 21, 2026 (America/New_York), using an isolated portable folder. The user's saved callsign, passcode, maps and chat database were not changed. Updated executable SHA-256: `97e7103c205015c028c2cfd40117c47e4659c636b95799dc2ba17d3397c9a565`.

## Live traffic and test method

Captured **16,810 current packets** from the APRS-IS full feed at `rotate.aprs.net:10152`, server EIGHTH, from 22:57:30 to approximately 23:00:31 local time. Average arrival rate: **93.3 packets/s**. The connection used an unverified receive-only login. No APRS packets were transmitted to the public network.

Port selection follows [APRS-IS connection documentation](https://www.aprs-is.net/Connecting.aspx). A bounded capture avoids leaving a full-feed connection running indefinitely. Raw captures, chat fixtures and local metrics stay under ignored `build/`; they are not committed to GitHub.

The raw packet sample was replayed over loopback into the actual portable application, through its normal APRS-IS transport, metadata processing, map, history and UI. Synthetic station injection was not used. Replay repetitions test load, not unique-station population. The current station store retains **2,000 stations** and expires older entries; this test does not prove unlimited station capacity.

## Findings fixed

1. **Incorrect coordinates for lowercase hemisphere letters.** Five real packets were mistaken for compressed reports and plotted far from their transmitted positions. Receive decoding now recognizes lowercase cardinal letters.
2. **Compressed/uncompressed format collision.** Four additional compressed packets happened to contain cardinal letters at the offsets checked for uncompressed coordinates. Detection now checks the numeric/decimal structure too.
3. **Valid positions lost with international comments or lowercase identities.** Display-only decoding tolerates international payload text and normalizes receive identities. RF/gating parsing and outbound encoding retain strict checks. The station store uses the same receive decoder.
4. **History throughput bottleneck.** One SQLite transaction per packet dropped 18,159 of 30,100 packets in the pre-optimization 1,000/s run. Available history records are now committed in batches of up to 100, without waiting to fill a batch on a quiet feed. A regression test verifies all 1,200 queued records reach disk on shutdown.
5. **Weather filter omitted weather-reporting stations using other symbols.** It now also checks received weather metadata.
6. **Unclear first-use and disabled controls.** Added first-connection guidance, readable connection states, a Clear filters action, trimmed recipient input, and an explanation for invalid message characters. Existing sent/acknowledged text remains prominent.

## Decoder comparison

Independent reference: `aprslib 0.7.2`. Compared positions within 0.0002 degrees (about 22 m of latitude), plus symbol/table identity. This is cross-implementation evidence, not a claim that either implementation is the APRS standard.

- APRSERJ64 decoded **12,500 positional reports** from the sample.
- Reference parser provided positions for **12,462 packets**.
- **12,368 common positions matched**, with **zero coordinate or symbol mismatches** after the fixes.
- **94 reference-decoded positions remain unhandled by APRSERJ64.** Most include nonstandard multi-character SSID suffixes such as DEMO-002; others include legacy UI wrappers or unusual payload forms. These are not silently counted as successful decodes. Indexes remain in the local mismatch report for further interoperability work.
- APRSERJ64 also decodes positional reports the reference does not. The counts above are intentionally not a fabricated universal success percentage.
- Final decoder probe: **20,344 packets/s** including position/message/weather/telemetry calls and diagnostic serialization. Station-store probe: **29,186 packets/s**. These are warmed JVM probes; they exclude the UI, networking and SQLite and must not be presented as end-to-end throughput.

## End-to-end replay results after batching

| Requested rate | Actual send rate | Packets sent | History drops | Drain after sender stopped | UI dispatch p95 / maximum | Peak process working set |
| --- | --- | --- | --- | --- | --- | --- |
| 1,000/s | 988/s | 30,200 | 0 | 1.2 s | 19.7 / 54.2 ms | 1113 MiB |
| 2,000/s | 1,971/s | 60,000 | 0 | 27.2 s | 39.8 / 66.7 ms | 1159 MiB |

Both stages ran about 30 seconds. All **90,200 packets** were ultimately processed, with zero recorded history drops and zero client APRS transmissions. At 2,000/s the receiver accumulated a material backlog: this is burst tolerance, **not sustained real-time capacity at 2,000/s**. Around 1,000/s is approximately ten times the captured live rate, with a short final queue drain.

UI timing measures AWT event-dispatch responsiveness via a posted event once per second. It is not GPU frame time, FPS, network latency or proof of zero rendering stalls. The map was zoomed from local to regional/global scales, showing SVG markers, and station search and chat reopening remained usable during traffic. Peak total process memory includes native map/runtime resources; this short test is not a long-running leak test.

## GUI workflow checklist

| Workflow exercised through the GUI | Observed result |
| --- | --- |
| Fresh portable startup | Map tiles load from the configured pdeffenb provider; no account is invented |
| Open/cancel Connection | Account, passcode, server and filter controls visible; cancel returns to map |
| Local demo | Three stations and two conversations received, isolated on loopback |
| Click map station | Correct callsign chat opens; selected marker/label updates |
| Compose/send local message | Composer clears; delivered acknowledgment and echo reply appear |
| Restart and reopen conversation | Prior message, acknowledgment and reply persist |
| Invalid message character | Send disabled with visible corrective explanation after fix |
| Weather filter | Original omission reproduced; fixed filter shows received-weather stations |
| Clear filters | Restores the full retained station list in one click |
| Search while traffic arrives | Text entry and filtered station/map results respond |
| Zoom local/regional/global | Tiles and SVG symbols render; north remains up |
| Home | Recenters the map while keeping the current zoom |
| Settings during traffic | Beacon controls open; cancel preserves settings |
| Weather tools | Temperature, wind, humidity, pressure and rainfall shown for demo report |
| Telemetry tools | Eight sample values and chart displayed with channel labels |
| Track export | GPX saved, with the destination shown |
| Offline maps | Preview reports eight tiles; download completes with 8/8 available |
| Packet monitor during heavy traffic | Opens and displays current incoming packets |
| New GPS port wizard | Preset supplies NMEA/4800 baud; blank device blocks final Add with an explanation |
| Cancel port draft | Returns to unchanged zero-port configuration |
| Clean shutdown | Test windows close; no replay is left connected |

The beacon status showed normal demo beacons before the receive-only replay, and transmission was disabled for that replay. No physical GPS/TNC/RF hardware was tested. This pass did not attempt every advanced feature, an overnight soak, or an Internet message to a third-party operator.

## Learning-curve follow-ups

The most useful next UI work would be a compact first-run checklist (identity, position, first connection), a choice menu instead of cycling through many station types, and grouping basic settings ahead of advanced telemetry/gateway options. Those larger redesigns were not mixed into this repair pass.

## Reproduce without public transmissions

1. Capture a bounded sample with `scripts/capture_aprs_feed.py --call YOURCALL-SSID --output build/live-feed/packets.txt`. It uses pass -1 and sends only a login.
2. Set `APRSERJ64_FEED_FILE` to that absolute file and run the Gradle test suite. The opt-in `LiveFeedProbeTest` otherwise skips itself.
3. Install aprslib in a test environment and run `scripts/compare_aprs_feed.py build/live-feed`.
4. Set `build/live-feed/replay-control.json` to `{"rate":0}` and run `scripts/replay_aprs_feed.py --input build/live-feed/packets.txt`. It binds loopback only, port 18765 by default.
5. Use a separate portable app folder configured for `127.0.0.1:18765`, receive-only passcode, no RF ports and beaconing off. Set `APRSERJ64_QA_METRICS` to an absolute JSONL output path before launch for optional counters/UI timing. Normal launches do not record these metrics.
6. Set the replay rate to 1000 or 2000, then back to zero after a bounded interval. Compare sender counts with processed + dropped counters and allow queues to drain. Stop the helper after testing.

Build and automated validation: **124 passing tests (58 Windows, 66 shared core)** including the opt-in captured-feed probe; no failures. Desktop deployment replaces only the EXE, preserves user data, and keeps a backup of the prior EXE.
