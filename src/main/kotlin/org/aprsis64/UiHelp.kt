// SPDX-License-Identifier: AGPL-3.0-only
package org.aprsis64

/** Explanations are separate from short labels so specialist details do not crowd the screen. */
internal object UiHelp {
 private val actions=mapOf(
  "Connect" to "Enter your callsign-SSID and APRS-IS passcode, choose a server, and connect. A blank passcode receives only.",
  "Connection" to "Review your APRS-IS callsign, login, server and receive-area center. These are separate from your beacon position.",
  "Beacon now" to "Request a fresh position report using your configured position source and enabled outputs. Requires beaconing enabled, a valid position, and an available verified Internet or radio output.",
  "Settings" to "Change saved position, beacon, map and feature preferences. Search by name; changes take effect when you Save.",
  "Menu" to "Open station details, weather, offline maps, specialist tools, radio/GPS ports and packet diagnostics.",
  "Station details" to "Inspect the selected station's last position, received path, nickname and map overlays.",
  "Weather reports" to "Read weather sent by APRS stations and any enabled weather-alert service.",
  "Download maps" to "Preview and download a bounded map area from your configured tile provider for offline use.",
  "All tools…" to "Open the searchable collection of station, weather, track, messaging and specialist tools.",
  "Radio & GPS ports…" to "Set up serial/network devices and wire their routes graphically. Changes remain a draft until Apply changes.",
  "Packet monitor…" to "Inspect raw received and transmitted packets. RX means received; TX means accepted by a local output, not confirmed remote reception.",
  "Disconnect APRS-IS" to "Stop the Internet APRS connection. Separately configured radio/GPS ports remain active.",
  "Stop practice" to "Stop the isolated local demo. Practice messages and beacons do not go to public APRS-IS or RF.",
  "Practice with local demo" to "Explore sample stations, beacons and acknowledged chats on this PC without public APRS or radio transmissions.",
  "Try practice mode" to "Explore sample stations and messages before setting up your callsign. APRS traffic stays on this PC.",
  "About & license" to "View the program version, source repository and open-source licenses.",
  "Save" to "Validate and save this editor's preferences to settings.toml. Existing values in other sections are retained.",
  "Cancel" to "Close this editor without saving its draft changes.",
  "Close" to "Close this window. Running reception continues in the main application.",
  "Clear filters" to "Clear the station search and category filter. Received stations may still be limited by their age setting.",
  "Save as my starting view" to "Save the station search, category, ordering and tracked callsigns for the next start.",
  "Station list options" to "Save the current station list as your starting view, or clear its filters.",
  "Track selected station" to "Open an independent map that follows this callsign. Up to eight tracking windows can be saved.",
  "Zoom in" to "Increase map detail without changing your beacon position. North stays at the top.",
  "Zoom out" to "Show a wider map area without changing your beacon position.",
  "Home" to "Return this map to its configured starting center. This does not change your transmitted location.",
  "Follow station" to "Keep the selected station centered as new positions arrive. Select a station with a position first.",
  "Following" to "Following the selected station's received position. Click to stop following.",
  "Go to coordinates" to "Open one location field for coordinates, Google Maps coordinate links and Plus Codes. This moves the map, not your beacon position.",
  "Go" to "Center the map on the interpreted location shown in the preview. Review any typing corrections or short-code reference first.",
  "Chat" to "Open a conversation with this exact callsign and SSID, even if the station is not on your map.",
  "Chats" to "Return to your saved conversations. Each callsign-SSID has its own history and session draft.",
  "Send" to "Save this message to the outgoing queue. Ctrl+Enter also sends. Earlier pending messages do not prevent another submission; check each message's delivery status.",
  "Request acknowledgment" to "Ask the recipient to acknowledge each message. An acknowledgment confirms its receiver accepted the message, not that a person read it.",
  "Retry as new message" to "Create a new outgoing message with a new message ID. The earlier attempt stays in your history.",
  "Automatic (recommended)" to "Prefer an eligible learned local/radio route, then verified APRS-IS or eligible radio fallback.",
  "Radio only" to "Use an eligible RF output. If none is available the message remains queued; it will not fall back to APRS-IS.",
  "Internet only" to "Send through the verified upstream APRS-IS connection. Radio outputs are not used for this message.",
  "Hide delivery options" to "Collapse route and acknowledgment choices. Their saved values still apply to new messages.",
  "SSID" to "Choose the station suffix from 0 to 15. Different devices should use distinct callsign-SSID identities; 0 omits the suffix.",
  "Custom server" to "Enter your own APRS-IS server address and port instead of a saved preset.",
  "Connect on startup" to "Reconnect using this saved APRS-IS login when the program starts.",
  "Preview" to "Calculate the number of map tiles before downloading. High zoom levels can greatly increase the count.",
  "Download" to "Download the previewed map tiles from the configured provider into this portable installation's offline cache.",
  "Cancel download" to "Stop the current download. Tiles already downloaded remain available.",
  "Use downloaded base map only" to "Use the explicit offline tile cache and disable network overlays. Areas or zoom levels not downloaded can appear blank.",
  "Pause display" to "Freeze this packet-monitor view for inspection. Reception and transmission continue.",
  "Resume" to "Resume showing current packet-monitor entries.",
  "Export TNC2" to "Save the currently displayed packet snapshot as a text file in the exports folder beside the EXE.",
  "View alerts" to "Open the notification history, including received-message and watched-station alerts.",
  "Dismiss" to "Clear the current notification banner; conversation history remains stored.",
  "Dismiss notifications" to "Clear the notification list; this does not delete chat history or disable future notifications.",
  "Add a port" to "Choose a GPS, TNC or network service and follow the setup guide. Review the draft wiring before applying.",
  "+ Add port" to "Create a device connection through the setup guide. It becomes live only after Apply changes.",
  "Apply changes" to "Validate and save the port draft, then update running connections. The diagram shows proposed wiring until this succeeds.",
  "Undo" to "Undo the last unapplied port edit. Running connections are unchanged until Apply changes.",
  "Redo" to "Restore an undone port edit in the draft.",
  "Reload saved" to "Reload the last saved port configuration. You will be asked before discarding unsaved changes.",
  "Show all wires" to "Show every configured route instead of only the selected port's connections. Wires indicate configuration, not confirmed packet delivery.",
  "Done wiring" to "Leave wiring mode and keep the draft connections for review.",
  "Wire digipeater outputs on diagram" to "Select the radio outputs that may repeat packets received by this port. Apply the draft to activate changes.",
  "Duplicate" to "Copy this port into the draft with a new name. Review its device address and routing before applying.",
  "Remove port" to "Remove this port and its wires from the draft. Routes with no remaining destination are disabled; Undo is available before Apply.",
  "Remove" to "Remove the selected entry from this editor. For objects, deleting a configuration is different from transmitting an APRS kill packet.",
  "Keep editing" to "Return to the editor and retain your unsaved changes.",
  "Discard changes" to "Discard the unsaved port draft; keep the running and saved configuration.",
  "Refresh" to "Scan again for locally attached serial devices. You can also enter a COM device name manually.",
  "Choose detected device" to "Choose an attached serial device. Confirm that its baud rate and protocol match your GPS or TNC.",
  "Use this GPS" to "Use this port's valid NMEA fixes as your station position after applying the draft.",
  "Select & enable GPS" to "Enable this GPS port and select it as the position source in the draft.",
  "Use fixed position instead" to "Stop using this GPS as your position source and return to the configured fixed coordinates after Apply.",
  "Next" to "Continue to the next setup step; current entries remain in the draft.",
  "Back" to "Return to the previous setup step without losing the draft.",
  "Add to diagram" to "Add this configured device to the draft. Review its wires, then Apply changes to start it.",
  "WIDE1 fill-in" to "Replace the draft digipeater rules with the WIDE1-1 fill-in rule. Review the output ports before applying.",
  "WIDE2 trace" to "Replace the draft rules with WIDE2-2/WIDE2-1 trace rules. Review local RF policy and output paths before applying.",
  "Save nickname" to "Save a local display name for this station. Messages still use the original callsign-SSID.",
  "Locate" to "Center the main map on this station's latest received position.",
  "Add file" to "Validate and add this GPX file as a map layer. The original file stays in place.",
  "Reload files" to "Re-read the configured GPX files and update their map layers.",
  "Remove layer" to "Remove this file from the configured map layers. The original file is retained.",
  "Download TLEs" to "Fetch orbital elements from your configured service. Satellite predictions depend on recent data and a correct station position/time.",
  "Predict passes" to "Calculate upcoming passes using the loaded orbital elements and your configured station location.",
  "Send email" to "Queue APRS messages to the configured email gateway. Long text becomes separate numbered emails; delivery depends on that gateway.",
  "Send QRU query" to "Ask the configured QRU service for nearby objects in this group. Replies appear in chat and positioned objects on the map.",
  "Transmit DX status" to "Send a status summary of recently heard radio stations through an eligible output.",
  "Read weather input" to "Read and validate the configured local weather data, including freshness and required fields.",
  "+ Add rule" to "Add a digipeater rule to the draft. Specify its matching alias, replacement path and output before applying.",
  "Add to map" to "Add the selected data to the map display.",
  "Browse" to "Choose a local file using the Windows file picker.",
  "Choose symbol" to "Choose the APRS symbol sent with this position or object. The symbol table selects primary, alternate or overlaid artwork.",
  "Choose which objects use this port →" to "Open the object routing table to select Internet and radio destinations for each object.",
  "Create object" to "Create a configured APRS object from these entries. Review its position, schedule and output route in Settings → Objects.",
  "Current ISS radio status" to "Open the configured ISS radio-status website in your browser. Orbital predictions alone do not indicate which radio is operating.",
  "Dead reckoning" to "Show estimated movement from the last received speed and course. The prediction is not a new position report.",
  "Edit" to "Open this saved entry for editing. Review the draft before saving or applying it.",
  "Enable station beacon output to RF" to "Allow station position reports on eligible radio ports. Each port must also permit transmission and beacons.",
  "Enable the master beacon switch" to "Enable scheduled station/object beacons and Beacon now. A valid position and an available configured output are still required.",
  "Fetch alerts for the configured fixed location" to "Request weather alerts near the fixed latitude/longitude in Settings. Panning the map does not move this reference.",
  "Import boundaries" to "Import a SHP/DBF boundary dataset or ZIP for geographical weather-warning overlays. Configure its code column in Settings → APRS weather.",
  "Keep port" to "Cancel removing this port and return to its draft configuration.",
  "Load file" to "Load orbital elements from the entered TLE file path. Check the epoch before relying on pass predictions.",
  "New" to "Create a new object draft. Set its name, position, symbol, schedule and destinations before saving.",
  "No serial devices detected" to "No COM devices were found. Attach the GPS/TNC and Refresh, or enter its device name manually.",
  "Only incomplete or malformed frequency reports" to "Filter the list to received frequency reports needing attention. This does not change reception or the original packet.",
  "Remove from settings" to "Remove this object configuration when settings are saved. To withdraw an already published object, turn Alive off and transmit its kill report first.",
  "Remove rule" to "Remove this digipeater rule from the draft. Running rules change only after Apply changes.",
  "Rename" to "Rename this port in the draft and update routes that refer to it. Apply changes to save the new name.",
  "Report and reset" to "Request an odometer status report and reset the recorded trip distance. Transmission requires an eligible output.",
  "Report odometer" to "Request a status report of accumulated travel distance without resetting it.",
  "Routes" to "Open the object routing table to choose which objects may transmit through each Internet or radio output.",
  "Satellite paths only" to "Show packet-monitor entries with recognized satellite path callsigns. Other traffic continues to be received.",
  "Save scheduled bulletin" to "Save this bulletin text and schedule for repeated transmission when bulletin output is enabled.",
  "Save this view" to "Save this tracking window's callsign and display choices for future starts.",
  "Send position in message" to "Send the selected station's last recorded position to the entered recipient callsign. This uses recorded history, not the map center.",
  "Send status" to "Request a station status packet using the saved status text and configured outputs.",
  "Show it as a port" to "Add this connection to the port workspace so its configuration and wiring can be reviewed.",
  "Show tracked satellites" to "Show predicted satellite locations using loaded orbital elements and the current clock.",
  "Source & license" to "Open this project's source repository and licensing information in your browser.",
  "Trails" to "Draw retained position history behind stations. Trails represent received positions, not a road route.",
  "Transmit bulletin" to "Request one transmission of the entered APRS bulletin through an eligible configured output.",
  "Transmit saved bulletins" to "Enable scheduled transmission of saved bulletins. Their intervals and output availability still apply.",
  "Use profile" to "Fill this port draft from the selected device profile. Confirm its device address, protocol, baud rate and wiring before applying.",
  "View settings" to "Open preferences to review the saved position, beacon and output choices."
  ,"Packet path" to "Draw the received packet's known digipeater hops. Missing locations and unexpanded aliases can leave gaps."
  ,"Estimated moving positions" to "Predict a station's location from its last reported speed and course. Estimates are distinct from received fixes."
  ,"PHG range circle" to "Show estimated radio range from reported power, antenna height and gain. It is not a measured coverage boundary."
  ,"Radio horizon" to "Show an estimated radio-horizon circle from reported antenna height. Terrain and radio conditions can change actual coverage."
  ,"Join quietly" to "Send a group membership request without announcing to the group. Check the server response for confirmation."
  ,"Leave" to "Request removal from the entered APRS message group. Check the server response for confirmation."
  ,"Announce" to "Send the entered announcement through the configured APRS group server."
  ,"List groups" to "Ask the configured APRS group server for its available group list."
  ,"My groups" to "Ask the configured APRS group server for your current memberships."
 )
 fun action(label:String):String {
  actions[label]?.let {return it}
  if(label.startsWith("Delivery:"))return "Show this conversation's route and acknowledgment options. Changes apply to new messages; queued messages retain their original options."
  if(label.startsWith("Export "))return "Export the selected station's recorded track as ${label.removePrefix("Export ")}. The file is saved in exports beside the EXE."
  if(label.startsWith("Hide "))return "Collapse these controls while retaining their values."
  if(label.startsWith("Show "))return "Show or hide ${label.removePrefix("Show ").lowercase()}. This controls the display, not packet reception."
  if(label.startsWith("Send ") || label.startsWith("Transmit "))return "$label through an eligible configured APRS output. Check the packet monitor or message status for the result."
  if(label.startsWith("Remove "))return "Remove this selected entry from the current configuration or view. Review any confirmation before continuing."
  if(label.startsWith("Save "))return "Save these choices for future use in this portable installation."
  return "$label. Select this option for the current view or editor. Disabled controls become available when their required inputs or selection are ready."
 }
 fun field(label:String):String=when {
  label.contains("Passcode",true)->"Enter the APRS-IS passcode for your callsign, or explicitly select Receive only. The passcode is saved as readable text in your local settings.toml; it is never displayed in this tooltip."
  label.contains("Callsign",true)->"Enter the exact callsign and optional -SSID suffix (0–15), for example N0CALL-13. Different suffixes are separate stations/conversations."
  label=="Location"->"Paste decimal degrees, degrees/minutes/seconds, degrees and decimal minutes, APRS coordinates, a Google Maps coordinate URL, or a Plus Code. Latitude comes first unless directions specify otherwise. Short Plus Codes use the map center. Review the preview before Go."
  label=="Message"->"Enter up to 300 printable ASCII characters. Longer APRS text is split into independent parts. Reserved {, | and ~ and line breaks are not accepted. Ctrl+Enter sends; Tab moves to the next control."
  label in listOf("South","North")->"${label} edge of the download area, in decimal latitude (-90 to 90). South must be less than north."
  label in listOf("West","East")->"${label} edge of the download area, in decimal longitude (-180 to 180). West must be less than east; split areas that cross the date line."
  label=="Find station" || label=="Station search"->"Filter the received station list by callsign text. This does not change what the server sends. Clear filters to show all matching-age stations."
  label=="Find a section"->"Search section names and familiar terms such as GPS, map, messages or GPX. Select a result to open it; other edits remain in your draft."
  label.contains("latitude",true)->"Latitude in decimal degrees: -90 to 90. North is positive; south is negative. Filter coordinates choose reception coverage; fixed coordinates choose your beacon location."
  label.contains("longitude",true)->"Longitude in decimal degrees: -180 to 180. East is positive; west is negative. Changing the map center does not change this setting."
  label.contains("URL",true)->"$label. Enter the configured service address. Tile templates must retain {z}, {x} and {y}; server fields accept a host and port."
  label.contains("host:port",true)->"Enter the device or server's network hostname/IP and TCP port, separated by a colon. Use [IPv6]:port for IPv6."
  label.contains("baud",true)->"Serial speed in bits per second. It must match the GPS/TNC setting; the wrong speed prevents useful decoding."
  label.contains("Serial device",true) || label.contains("serial port",true)->"Enter the Windows COM device name or choose a detected device. Confirm it belongs to your GPS/TNC; serial speed must match."
  label.contains("file",true) || label.contains("path",true) && !label.contains("RF",true) && !label.contains("Remaining",true)->"$label. Use a readable file path. Relative file paths are resolved from the folder containing this installation."
  label.contains("RF path",true) || label.contains("Remaining path",true)->"APRS radio path: comma-separated digipeater callsigns/aliases, such as WIDE1-1,WIDE2-1. Internet q constructs must not be used as an RF path."
  label.contains("Object name",true)->"Enter an APRS object/item name. Object names are limited to nine characters; choose a name that identifies what you are publishing."
  label.contains("Local nickname",true)->"A display-only name for this station. Its transmitted callsign and chat identity stay unchanged."
  label.contains("zoom",true)->"Map zoom level: larger numbers show more detail. Offline downloads at high zoom need many more tiles. Stay within the configured provider's limits."
  label.contains("MHz",true)->"Frequency in megahertz, for example 146.940. This describes a repeater/object; it does not tune a radio."
  label.contains("Tone",true)->"APRS frequency tone text, such as T123 for 123.0 Hz or Toff for no tone."
  label.contains("Offset",true)->"APRS repeater offset in 10 kHz units: +060 means +0.600 MHz; -060 means -0.600 MHz."
  label.contains("channel",true)->"Select the device's zero-based radio/channel index. KISS and AGW devices must agree with the configured channel."
  label.contains("seconds",true)->"$label. Enter seconds, not minutes. This controls timing/expiry for the named feature."
  label.contains("minutes",true)->"$label. Enter minutes. Lower age limits discard stale data sooner."
  label.contains("Radius",true)->"Receive/query radius in kilometers around the configured reference location. It is not the visible map zoom."
  else->"$label. Enter the value for this control; review units in the label. Settings editors retain changes as a draft until Save or Apply."
 }
 fun section(title:String,keywords:String):String {
  val purpose=when(title) {
   "My position"->"Choose fixed coordinates or a GPS source for your station's position."
   "Beaconing"->"Set automatic position reporting, symbol, timing and allowed outputs."
   "Map appearance"->"Choose the tile provider, attribution, zoom and map layers."
   "Station display"->"Choose station categories, ordering, age limits and map display preferences."
   "Messages"->"Configure delivery retries, acknowledgments and message preferences."
   "Receive area & filters"->"Choose which APRS-IS packets to request. This is separate from map zoom and beacon position."
   "Station status"->"Configure the APRS status text and its transmission schedule."
   "Objects & items"->"Publish named locations with individual positions, symbols, schedules and destinations."
   "History storage"->"Set how received tracks and packet history are retained locally."
   "Service addresses"->"Edit service URLs used by maps and other data sources."
   "Connection recovery"->"Configure reconnection timing and network recovery behavior."
   "Selected station"->"Inspect the selected station, locate it on the map or give it a local nickname."
   "Telemetry charts"->"Chart measurements reported by APRS stations and inspect their parameter definitions."
   "GPS reception"->"Inspect satellite visibility and fix quality reported by GPS ports."
   "Find nearby objects"->"Request named groups of nearby objects from an APRS QRU service."
   "Radio reach"->"Inspect recently heard radio stations and distance records."
   else->actions[title] ?: "Open $title controls${if(keywords.isBlank()) "." else ", including $keywords."}"
  }
  return purpose+" Selecting a section keeps your other draft entries."
 }
 fun setting(f:BeaconField):String {
  val detail=when(f.key) {
   "beacon.enabled"->"Master switch for scheduled station/object beacons and Beacon now. Position reports use the selected source and enabled outputs."
   "beacon.internet"->"Allow station beacons through a verified APRS-IS connection. RF beacon output has its own switch."
   "beacon.radio"->"Allow station beacons through eligible radio ports. Each port's transmit and beacon switches must also allow output."
   "position.source"->"Choose fixed coordinates or a GPS source. Only relevant fields are shown; hidden source settings are preserved."
   "display.kind"->"Choose which received station categories are shown. This does not change the server feed."
   "display.sort"->"Order the station list by callsign, most recent reception or distance from your configured station position."
   "display.maximum_age_minutes"->"Hide stations older than this many minutes since reception. Reception continues and retained history is separate."
   "tactical.dead_reckoning"->"Estimate a moving station's present position from its last speed/course. The estimate is not a new received fix."
   "offline.enabled"->actions.getValue("Use downloaded base map only")
   "map.tile_url"->"Raster tile template for the base map. Retain {z}, {x}, {y}; attribution and zoom bounds should match this provider."
   "map.attribution"->"Visible credit for map data and the tile provider. Retain the attribution required by your provider's license."
   "map.attribution_url"->"Page opened when the map attribution is clicked."
   "digital.enabled"->"Read supported FT8, JS8Call or JT65 receive logs and display located stations. APRSIS64 does not decode audio."
   "digital.transmit"->"Publish imported digital spots as APRS objects. Review their locations and output route before enabling."
   "enabled"->"Enable this configured object or port. Other transmit, route and master switches still apply."
   "alive"->"Alive publishes the object/item. Turning it off schedules kill packets; removing the settings entry alone is different."
   "query_only"->"Publish this object only in response to a matching query, rather than on its normal periodic schedule."
   else->if(f.kind==SettingKind.BOOLEAN) "Enable or disable ${f.label.lowercase()}. Other output and master switches may still apply." else field(f.label)
  }
  val format=when {
   f.choices.isNotEmpty()->" Choices: "+f.choices.joinToString {it.ifBlank {"None"}}+"."
   f.kind==SettingKind.STRINGS->" Enter one item per line."
   f.kind==SettingKind.INTEGER->" Use a whole number."
   f.kind==SettingKind.DECIMAL->" Use a number with a decimal point if needed."
   else->""
  }
  return detail+format+" Saved as ${f.key} in settings.toml."
 }
 fun indicator(text:String):String=when {
  text=="APRS-IS connected and verified"->"The APRS-IS server accepted your login for transmission. A ready connection does not confirm delivery of any particular packet. Check beacon or chat status for actual sends."
  text.contains("Practice",true)->"Local practice mode: sample APRS stations and conversations stay on this PC and are isolated from public APRS-IS and RF."
  text.contains("APRS-IS",true) && text.contains("receive",true)->"The server connection can receive packets, but this login has not been verified for APRS-IS transmission. Check the callsign and passcode."
  text.contains("Disconnected",true) || text.contains("not connected",true)->"The Internet APRS connection is stopped. Separately configured radio/GPS devices can still operate. Use Connect to start APRS-IS."
  text.contains("reconnect",true)->"The connection was interrupted. The client is waiting/retrying according to its saved recovery settings."
  text.contains("connecting",true)->"Opening the server connection and waiting for the login response. Transmission requires server verification."
  text.startsWith("Beacon") || text.startsWith("Last sent") || text.contains("GPS fix",true)->"Beacon status: the selected position source, availability and last accepted output. A local send is not proof of reception elsewhere. GPS positions must be current and valid."
  text.contains("RX") || text.contains("TX")->"RX counts received packets; TX counts packets accepted by a local output. Dropped counts omitted packets. None of these counters proves remote reception."
  text.contains("acknowledgment",true)->"Each outgoing APRS message has its own status. An acknowledgment confirms the addressed receiver accepted it; it does not mean a person has read it. Retries follow your saved messaging limits."
  text.startsWith("Queued")->"Saved locally for delivery when an eligible connection/route becomes available. You can compose and queue additional messages."
  text.startsWith("Sent")->"The local output accepted this packet. Remote receipt is confirmed only by an appropriate reply or message acknowledgment."
  text.contains("Pending Apply",true) || text.contains("Unsaved changes",true)->"This is a proposed configuration. Running connections continue using saved settings until Apply changes succeeds."
  text.contains("received",true)->"Information decoded from a received packet. Check its reception time; stale positions may no longer represent the station's current location."
  text.contains("from your position")->"Straight-line distance and bearing from your station position to the selected received location. This is not a road route."
  text in actions->actions.getValue(text)
  text=="STATIONS"->"Stations with recent received positions. Search and category/age filters affect this list; they do not stop reception."
  text=="MESSAGES"->"Saved conversations for your current callsign-SSID. Select a thread or enter another callsign to start chatting."
  text=="Nearest first"->"Sort by straight-line distance from your configured station position, not the current map center."
  text=="Fixed location"->"Use the manually configured latitude, longitude and altitude as your beacon position. Panning the map does not change it."
  else->text.ifBlank {"No value has been reported yet."}
 }
}
