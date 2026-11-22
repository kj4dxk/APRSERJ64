# Hover help, status clarity and location entry — November 22, 2026

## Changes

- Verified public connections display **APRS-IS connected and verified**. Receive-only, connecting, reconnecting, stopped and isolated practice states have distinct labels. Hover help distinguishes login verification, local output acceptance and message acknowledgment.
- Added hover help to more than 220 button, field, checkbox, switch and menu call sites, plus text indicators, section navigation, icon actions and symbol choices. Schema-driven settings explain units and their TOML keys. Disabled controls also show help.
- Combined map Go-to coordinates into one Location field with a normalized preview, correction explanation and disabled Go for invalid input. Panning and Go-to do not change the station's beacon position.
- Accepted inputs include decimal degrees, DMS, degrees/decimal minutes, compact APRS coordinates, coordinate-bearing Google Maps URLs and Open Location Codes. Short Plus Codes recover near the map center captured when the entry is edited. Google’s official Open Location Code Java library supplies decoding and recovery; its Apache license is bundled.
- Corrected the compact APRS parser for leading-zero coordinates near the equator/prime meridian. Added regression examples for both hemispheres and invalid minutes.
- Station details show six-decimal coordinates and readable timestamps with the local time-zone abbreviation.

## Automated checks

- Full `test packagePortable` succeeded: 132 tests passed (66 application and 66 shared-core), zero failures; one optional live-feed capture probe skipped because this run did not supply a capture.
- After the final low-latitude compact-coordinate correction, all nine coordinate/status test methods passed again and the portable package rebuilt successfully. Those methods cover multiple decimal, DMS, DMM, APRS, Google-link, Plus Code, typo and malformed-input examples.
- Reviewed accepted/rejected URL behavior: explicit single pin takes priority over viewport, multiple pins fail, short links without coordinates fail with guidance, arbitrary hosts/addresses are not parsed as positions.
- Manual validation passed for all 29 chapters, configuration examples, local links, anchors and standalone HTML links.
- Portable application JAR contains all 188 SVG symbol files and the Open Location Code license.

## Observed Windows GUI checks

Used a fresh portable copy in `build/gui-help` with the isolated local practice feed. This was a focused regression pass, not a repeat of every specialist workflow or the full-feed performance test.

| Workflow | Observed result |
| --- | --- |
| Fresh practice launch | Main toolbar, map, station list and saved-chat list rendered normally; practice wording explicitly said traffic stays local |
| Hover disabled Follow station | Explanatory tooltip appeared without activating the action |
| Open location entry | One full-width field, Go button and preview panel appeared |
| Paste `849VCWC8+R9`, then Go | Preview showed the code’s center and map moved to Mountain View; north remained up |
| Replace it with `CWC8+R9` | Same location resolved; preview named the captured map reference and recovered full code |
| Enter repeated decimal point and O/I substitutions | Corrected numeric preview appeared with an explicit correction notice; map did not move merely from typing |
| Enter out-of-range `95,100` | Red validation guidance appeared and Go was disabled |
| Settings field hover | Readable explanation and exact TOML key appeared within the dialog |
| Position-source dropdown hover and selection | Choice help appeared; Serial GPS selected correctly and relevant fields retained full width |
| Beacon checkbox hover | Master-switch explanation appeared |
| Cancel settings | Test edits were discarded; fixed beacon source remained active |
| Open conversation and send using Ctrl+Enter | Composer submitted and cleared; message progressed from queued to acknowledged by the local peer; reply appeared |
| Main Menu and All tools | Menu sizing/clicks worked; grouped Tools navigation and station details rendered |
| Radio & GPS ports and Add a port | Workspace, disabled Apply, profile buttons and setup guide rendered; temporary draft canceled |

No test conversations were sent to other operators or RF. Local practice exercised acknowledgment display; it does not establish public delivery or physical TNC interoperability.

## Limits and deployment

- Recognizable numeric lookalikes are repaired and flagged; unknown/ambiguous typos are rejected rather than guessed.
- Short Plus Codes require a nearby map reference. Code-plus-place-name geocoding, street-address search and online expansion of short Google sharing links are not provided by this parser; the UI and manual give corrective guidance.
- The combined flexible entry is the map Go-to control. Saved fixed-position, receive-filter and bounding-box settings retain their explicit coordinate fields.
- Desktop EXE was backed up and replaced. SHA-256 matched the build; settings and six data files were unchanged during replacement. The updated offline manual was copied alongside it.
- Launched the updated desktop copy with the existing saved account: public APRS-IS showed **APRS-IS connected and verified**, incoming stations appeared, and the existing conversation remained visible. This verifies startup/reception and the login label, not remote delivery of an individual beacon.

Desktop/build EXE SHA-256: `C50AEBC0FBA0F43BAC0BFAEF5A6ED865B8D852937A7D5A22EC2F7171C69A092F`.
