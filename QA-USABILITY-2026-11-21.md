# Everyday-workflow usability pass — November 21, 2026

This pass used the actual Windows GUI on the development PC, with a fresh portable installation in `build/gui-usability`. It focused on first use, finding controls, interrupted conversations, and everyday map tasks rather than repeating the earlier full-feed stress test. These are representative workflows, not a measured claim about 90% of operators.

## Design changes

- Reduced the main toolbar from eight actions to four: Connection, Beacon now, Settings, Menu. Specialist tools and diagnostics remain accessible from Menu.
- Kept words on ambiguous actions. Added small vector icons for familiar actions, accessible icon descriptions and hover labels on icon-only controls.
- Replaced the flat 29-section Settings and 19-section Tools button strips with grouped, searchable navigation. Search also recognizes former section names and common terms such as GPS and GPX.
- Settings opens at My position. Only fields relevant to the selected position source are shown; other saved values are retained. Fixed location no longer presents every GPS transport at once.
- Replaced cycling station type, sort and chat route buttons with direct-choice menus. All Settings choice fields show a dropdown arrow.
- Kept chat delivery choices collapsed by default, with Automatic, Radio only and Internet only labels. Retained acknowledgment options and delivery states.
- Preserved unsent drafts separately per account/callsign while switching conversations. Added Ctrl+Enter to send through the same submission path as Send. Drafts are session-only; message history and queued messages remain persistent.
- Hid coordinate-entry fields until requested, improved invalid-coordinate guidance, cleared obsolete errors when closing that control, and disabled Follow when no selected station position exists.
- Fixed the formerly empty Digital settings section. Menu shortcuts and View alerts now select and focus the intended Tools page even when that window is already open.
- Updated the manual's control names and navigation instructions.

## Observed GUI results

| Workflow | Result |
| --- | --- |
| Fresh launch without an identity | Map loaded; setup guidance and Try practice mode were visible; beacon send unavailable |
| Open Connection and cancel | Callsign, passcode, server and receive-area controls displayed; returned cleanly |
| Start practice from first-run guidance | Three demo stations and two conversations appeared; practice identity shown |
| Choose Weather directly | One of three demo stations remained in the list |
| Clear filters | All three stations returned |
| Choose Nearest first and save starting view | Choice displayed correctly; TOML saved `display.sort = 'distance'` |
| Select a station | Map centered and matching conversation opened |
| Switch callsigns with an unfinished draft | Draft remained associated with the original callsign |
| Send using Ctrl+Enter | Draft cleared after saving; local peer acknowledged and replied |
| Enter a reserved message character | Send disabled and actionable validation shown; correcting it re-enabled Send |
| Queue a Radio only message without a radio | Clear queued state shown; composer remained usable |
| Send a later Automatic message to the same peer | Later message was acknowledged while the earlier radio-only message remained pending |
| Open a callsign absent from the map | Lowercase input normalized; conversation opened without requiring a position |
| Return to Chats and restart application | Conversation previews and previous message history remained available |
| Settings search for digital | Matching section found; import fields displayed and Settings saved successfully |
| Fixed location versus Serial GPS | Final build showed only relevant fields; Cancel retained the saved fixed source |
| Open selected-station details | Correct callsign and received information displayed |
| View weather reports | Demo weather values displayed in the grouped Tools page |
| Search Tools for GPX | Track export and GPX import appeared as direct results |
| Export a practice track | GPX file saved; parsed successfully with two track points |
| Menu → Download maps | Opened the intended Tools section directly |
| Preview and download a small area | Eight tiles planned; completion reported 8/8 available |
| View alerts while Tools was already open | Existing window came forward on Notifications |
| Beacon now | Practice beacon sent and last-sent status updated |
| Menu → Packet monitor | TX beacon and received practice packets visible |
| Menu → Radio & GPS ports | Existing graphical workspace and first-device guidance opened |
| Coordinate entry and empty submission | Final build showed coordinate ranges; collapsing the control removed its error |

Tests used the local demo for APRS messages and beacons. The map download used the configured pdeffenb provider. No test conversations were sent to public APRS-IS or RF. Physical serial/TNC interoperability and full-feed performance were not repeated in this pass; see the separate live-feed report.

## Build and repository checks

- `test packagePortable`: successful; 123 tests passed, zero failures, one optional live-feed capture probe skipped because no capture was supplied for this pass (57 Windows tests plus 66 shared-core tests passed).
- Final Kotlin compile emitted no compiler warnings.
- Manual validation: all 29 chapters, configuration examples, local links, anchors and offline links passed; searchable site and standalone HTML rebuilt.
- `git diff --check`: clean.
- Portable desktop executable replaced after backing up the previous EXE; settings and databases preserved. Build and desktop SHA-256 hashes verified equal.

The screen checks used the PC's current display scaling and ordinary application windows. They do not establish accessibility certification, every possible window size, or every specialist workflow.
