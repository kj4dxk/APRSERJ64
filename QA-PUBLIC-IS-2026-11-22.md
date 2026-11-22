# Public APRS-IS acceptance — November 22, 2026

## Results

- Two independent APRSIS64 application models used the operator's locally saved credentials with distinct test SSIDs. Neither credentials nor raw operator logs are included in this repository.
- Sent 100 unique messages, alternating directions at four-second intervals. Both databases contained 50 incoming messages and 50 acknowledged outgoing messages. All 100 distinct bodies reached their intended receiving application. No test conversations were addressed to other operators.
- Ran two moving-position sessions using a real loopback TCP NMEA connection. The simulator produced checksum-bearing, current UTC RMC sentences once per second, moving north by about 0.56 meters per second and reporting one knot. Beacons used a 30-second maximum interval; telemetry used a 60-second interval.
- Each moving session produced 15 station position/telemetry outputs. Every transmitted payload was found in the independent receiver's APRS-IS input, with zero invalid test packets. Each receiving history retained seven position reports and eight telemetry/definition observations. The receiving station model advanced north of its starting latitude.
- The second moving run captured server comments, required verified login responses for both connections, and found no error, invalid, rejected or unverified notices. APRS-IS can silently discard packets; the independent payload comparison is the stronger delivery evidence.
- In the desktop GUI, selected the live moving test station and opened a tracking window. Following stayed enabled, north stayed up, the label showed approximately 2 km/h, and the map/distance updated as new reports arrived.

These results establish delivery to the participating receiving connections, not every APRS-IS server or third-party website. They do not test RF or physical GPS hardware. Test instances and their TCP simulator were shut down after each run; the normal desktop instance remains available to the operator.

## Fixes and checks

- Added bounded server-response history in the shared engine, with passcode redaction. Packet Monitor now has **Show server responses**. A loopback regression checks banner capture and credential redaction.
- Final ordinary suite: 132 passed, zero failures/errors, three skips (two explicitly opt-in public-network tests and one optional capture probe). The public-network tests were run separately as described above.
- Built the latest source and portable EXE; validated all 29 manual chapters. Desktop replacement preserved settings and databases. The public release contains only the clean portable EXE and manual, not the configured desktop folder.
- Added About links for both manuals, the old wiki, credits/licenses, APRS Foundation, Foundation GitHub/APRSISCE source, the APRSIS32 repository and passcode instructions. Links remain configurable in TOML.
- Published a direct EXE download link and first-connection instructions on the default GitHub branch. Passcode instructions link to the legacy manual and retain its restriction to APRSISCE/32 users; users should identify their actual application when requesting assistance.

## Account-default audit

- APRSIS64's distributed callsign/passcode fields are blank. A scan using the operator's actual local account values found no literal credentials in tracked text after anonymizing two older test reports. Local settings/data/logs remain ignored.
- In `kj4dxk/APRSIS32`, cleared four populated callsign tags in distributed XML configurations; their password tags were already empty. Also sanitized a historical commented login example.
- In `APRSFoundation/APRSISCE`, found no tracked XML account configurations. Its source defaults to the generic `NOCALL` placeholder, not an operator identity. Sanitized the historical commented login example there too. Author attributions remain intact.
- Changes were pushed to both repositories. Existing Git history was not rewritten; earlier revisions can still contain historical examples.

Release EXE SHA-256: `8bcd1f8ff72cfbf298910035828d4ec4c33f5ba2de2c90e7b9190800b6e39d2a`.

[Download release](https://github.com/kj4dxk/APRSIS64/releases/tag/portable-2026-11-22)
