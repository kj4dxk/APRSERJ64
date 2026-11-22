# Settings catalog

Generated from `settings.example.toml` and the application field schema. These are **new-install example defaults**, not a promise that an existing installation has these values. See [editing TOML](files.md) before changing a file. Exact keys are searchable.

Tables containing records (server presets, ports and objects) are explained separately. Service addresses are configurable; a default address does not guarantee service availability. Routing and setup endpoints are omitted from this catalog; use their configured values in your local `settings.toml`.

## aprs

| Key | Example default | Meaning / UI label |
| --- | --- | --- |
| `aprs.callsign` | `""` | callsign |
| `aprs.passcode` | `""` | passcode |
| `aprs.server` | `"rotate.aprs2.net"` | server |
| `aprs.port` | `14580` | port |
| `aprs.connect_on_startup` | `true` | connect on startup |
| `aprs.radius_km` | `100` | radius km |

## aprs.servers records

Each `[[aprs.servers]]` entry is a separate record. The example contains 12 entries.

- `name = "Main aprs2.net rotate — rotate.aprs2.net"`; `host = "rotate.aprs2.net"`; `port = 14580`
- `name = "North America — noam.aprs2.net"`; `host = "noam.aprs2.net"`; `port = 14580`
- `name = "Europe — euro.aprs2.net"`; `host = "euro.aprs2.net"`; `port = 14580`
- `name = "Asia — asia.aprs2.net"`; `host = "asia.aprs2.net"`; `port = 14580`
- `name = "Australia and New Zealand — aunz.aprs2.net"`; `host = "aunz.aprs2.net"`; `port = 14580`
- `name = "South America — soam.aprs2.net"`; `host = "soam.aprs2.net"`; `port = 14580`
- `name = "Core — rotate.aprs.net"`; `host = "rotate.aprs.net"`; `port = 14580`
- `name = "Germany — germany.aprs2.net"`; `host = "germany.aprs2.net"`; `port = 14580`
- `name = "Japan — japan.aprs2.net"`; `host = "japan.aprs2.net"`; `port = 14580`
- `name = "Nordic countries — nordic.aprs2.net"`; `host = "nordic.aprs2.net"`; `port = 14580`
- `name = "Switzerland — swiss.aprs2.net"`; `host = "swiss.aprs2.net"`; `port = 14580`
- `name = "China — china.aprs2.net"`; `host = "china.aprs2.net"`; `port = 14580`

## map

| Key | Example default | Meaning / UI label |
| --- | --- | --- |
| `map.latitude` | `28.536725` | latitude |
| `map.longitude` | `-81.242932` | longitude |
| `map.source_name` | `"pdeffenb"` | source name |
| `map.tile_url` | `"http://pdeffenb.homeip.net:82/raster/light/{z}/{x}/{y}.png"` | tile url |
| `map.attribution` | `"© OpenStreetMap contributors"` | attribution |
| `map.attribution_url` | `"https://www.openstreetmap.org/copyright"` | attribution url |
| `map.tile_size` | `256` | tile size |
| `map.minimum_zoom` | `0` | minimum zoom |
| `map.maximum_zoom` | `18` | maximum zoom |
| `map.initial_zoom` | `12.0` | initial zoom |
| `map.cache_megabytes` | `512` | cache megabytes |
| `map.maximum_fps` | `30` | maximum fps |
| `map.online_enabled` | `true` | online enabled |
| `map.base_opacity` | `1.0` | base opacity |
| `map.overlay_enabled` | `false` | overlay enabled |
| `map.overlay_url` | `""` | overlay url |
| `map.overlay_opacity` | `0.5` | overlay opacity |
| `map.underlay_enabled` | `false` | underlay enabled |
| `map.underlay_url` | `""` | underlay url |

## services

| Key | Example default | Meaning / UI label |
| --- | --- | --- |
| `services.satellite_status_url` | `"https://www.ariss.org/current-status-of-iss-stations.html"` | satellite status url |
| `services.base_url` | `"http://pdeffenb.homeip.net:82"` | base url |
| `services.routing_url` | See local `settings.toml` | routing url |
| `services.search_url` | `"http://pdeffenb.homeip.net:82/api/search"` | search url |
| `services.map_page_url` | `"http://pdeffenb.homeip.net:82/raster.html"` | map page url |
| `services.setup_url` | See local `settings.toml` | setup url |
| `services.monitor_url` | `"http://pdeffenb.homeip.net:82/monitor.html"` | monitor url |
| `services.source_url` | `"https://github.com/kj4dxk/APRSIS64"` | source url |

## beacon

| Key | Example default | Meaning / UI label |
| --- | --- | --- |
| `beacon.enabled` | `true` | Beaconing enabled |
| `beacon.internet` | `true` | Beacon to APRS-IS |
| `beacon.radio` | `true` | Beacon to RF ports |
| `beacon.smart` | `false` | Use speed-based SmartBeaconing |
| `beacon.smart_slow_knots` | `5.0` | Smart slow speed (knots) |
| `beacon.smart_fast_knots` | `60.0` | Smart fast speed (knots) |
| `beacon.smart_fast_seconds` | `120` | Smart fast interval (seconds) |
| `beacon.smart_turn_slope` | `255.0` | Smart turn slope (degrees × knots) |
| `beacon.smart_turn_seconds` | `30` | Smart minimum turn interval (seconds) |
| `beacon.minimum_seconds` | `10` | Minimum automatic interval (seconds) |
| `beacon.maximum_seconds` | `1800` | Maximum interval (seconds) |
| `beacon.time_only` | `false` | Use time only |
| `beacon.start_stop` | `true` | Beacon on start / stop |
| `beacon.heading_degrees` | `30.0` | Heading change (degrees; 0 disables) |
| `beacon.forecast_error_meters` | `160.9344` | Forecast error (meters) |
| `beacon.distance_meters` | `1609.344` | Maximum distance (meters) |
| `beacon.moving_knots` | `0.5` | Moving speed threshold (knots) |
| `beacon.on_reconnect` | `true` | Beacon on reconnect |
| `beacon.on_gps_change` | `true` | Beacon on GPS fix gained / lost |
| `beacon.recenter_after_transmit` | `false` | Recenter map after transmitting |
| `beacon.symbol_table` | `"/"` | Symbol table or overlay |
| `beacon.symbol` | `"-"` | Symbol code |
| `beacon.timestamp` | `"hms"` | Timestamp |
| `beacon.mic_e` | `false` | Transmit Mic-E positions |
| `beacon.compressed` | `false` | Compressed position |
| `beacon.course_speed` | `true` | Include course / speed |
| `beacon.altitude` | `true` | Include altitude |
| `beacon.precision` | `0` | Precision (0, 1, 2, 4; ambiguity -1 to -4) |
| `beacon.messaging` | `true` | Advertise messaging capability |
| `beacon.include_reason` | `false` | Include transmission reason |
| `beacon.status_tag` | `""` | Mic-E status tag |
| `beacon.comments` | `["APRSIS64"]` | Comments (one per line) |
| `beacon.comment_enabled` | `true` | Include comments |
| `beacon.comment_interval_seconds` | `0` | Minimum comment interval (seconds) |
| `beacon.rotate_comments` | `false` | Rotate comments |
| `beacon.selected_comment` | `0` | Selected comment (0 is first) |

## odometer

| Key | Example default | Meaning / UI label |
| --- | --- | --- |
| `odometer.report_on_exit` | `false` | Report odometer on exit |
| `odometer.minimum_meters` | `3.0` | Odometer jitter threshold (meters) |
| `odometer.maximum_kph` | `1000.0` | Odometer maximum plausible speed (km/h) |

## position

| Key | Example default | Meaning / UI label |
| --- | --- | --- |
| `position.source` | `"fixed"` | Position source |
| `position.port_name` | `""` | Named NMEA port |
| `position.latitude` | `28.536725` | Fixed latitude |
| `position.longitude` | `-81.242932` | Fixed longitude |
| `position.altitude_meters` | `0.0` | Fixed altitude (meters) |
| `position.gps_endpoint` | `"127.0.0.1:10110"` | gps endpoint |
| `position.gpsd_endpoint` | `"127.0.0.1:2947"` | gpsd endpoint |
| `position.serial_port` | `""` | GPS serial port (blank: use only attached port) |
| `position.serial_baud` | `4800` | GPS serial baud |
| `position.maximum_age_seconds` | `30` | GPS fix age limit (seconds) |
| `position.maximum_accuracy_meters` | `100.0` | GPS accuracy limit (meters) |

## status

| Key | Example default | Meaning / UI label |
| --- | --- | --- |
| `status.enabled` | `false` | Send status reports |
| `status.interval_seconds` | `1800` | Status interval (seconds) |
| `status.timestamp` | `true` | Include status timestamp |
| `status.grid` | `false` | Include grid instead of timestamp |
| `status.text` | `"APRSIS64"` | Status text |

## radio

| Key | Example default | Meaning / UI label |
| --- | --- | --- |
| `radio.heard_seconds` | `1800` | Local heard window (seconds) |
| `radio.maximum_hops` | `2` | Maximum local RF digipeater hops |
| `radio.duplicate_seconds` | `30` | Duplicate suppression (seconds) |
| `radio.internet_seconds` | `1800` | Internet presence window (seconds) |
| `radio.maximum_entries` | `4096` | Maximum heard/dedup entries |

## operating

| Key | Example default | Meaning / UI label |
| --- | --- | --- |
| `operating.filter_mode` | `"range"` | APRS-IS filter mode |
| `operating.custom_filter` | `""` | Additional / custom server filter |
| `operating.monitor_capacity` | `1000` | Packets kept in monitor |
| `operating.feed_enabled` | `false` | Share received packets with local apps |
| `operating.feed_port` | `14581` | Local receive-only TNC2 TCP feed port |
| `operating.satellite_aliases` | `["RS0ISS", "NA1SS", "ARISS", "APRSAT"]` | Satellite callsign prefixes (one per line) |

## alerts

| Key | Example default | Meaning / UI label |
| --- | --- | --- |
| `alerts.message_sound` | `""` | Message sound WAV file (empty: beep) |
| `alerts.station_sound` | `""` | Watched station sound WAV file (empty: beep) |
| `alerts.weather_sound` | `""` | Weather warning sound WAV file (empty: beep) |
| `alerts.connection_sound` | `""` | Connection change sound WAV file (empty: beep) |
| `alerts.connection` | `false` | Connection change notifications |
| `alerts.weather` | `false` | New severe weather notifications |
| `alerts.proximity_km` | `0.0` | Watched station proximity (0: any distance) |
| `alerts.lost_minutes` | `0` | Notify when watched station is lost (0: off) |
| `alerts.messages` | `true` | New message alerts |
| `alerts.watch_calls` | `[]` | Alert on callsigns (one per line) |
| `alerts.desktop` | `false` | Show desktop tray notifications |
| `alerts.sound` | `false` | Play alert sound |
| `alerts.minimum_seconds` | `30` | Minimum sound interval |

## bulletins

| Key | Example default | Meaning / UI label |
| --- | --- | --- |
| `bulletins.enabled` | `false` | Transmit saved bulletins |
| `bulletins.entries` | `[]` | Bulletins: slot&#124;group&#124;seconds&#124;is/rf/both&#124;text (one per line) |

## connectivity

| Key | Example default | Meaning / UI label |
| --- | --- | --- |
| `connectivity.failover` | `false` | Try other saved APRS-IS presets on failure |

## digital

| Key | Example default | Meaning / UI label |
| --- | --- | --- |
| `digital.enabled` | `false` | Import digital-mode logs |
| `digital.transmit` | `false` | Transmit imported digital objects |
| `digital.jt65_file` | `""` | JT65-HF CSV log file |
| `digital.js8_file` | `""` | JS8Call ALL.TXT file |
| `digital.ft8_file` | `""` | WSJT-X FT8 ALL.TXT file |
| `digital.maximum_age_seconds` | `1800` | Expire digital spots after (seconds) |
| `digital.interval_seconds` | `600` | Digital object interval (seconds) |
| `digital.maximum_objects` | `50` | Maximum objects per log |
| `digital.route` | `"is"` | Digital object outputs |
| `digital.ports` | `[]` | Digital object RF ports (empty: all eligible) |

## display

| Key | Example default | Meaning / UI label |
| --- | --- | --- |
| `display.search` | `""` | Station search |
| `display.kind` | `"all"` | Station type |
| `display.sort` | `"callsign"` | Station sorting |
| `display.maximum_age_minutes` | `30` | Station age limit (minutes) |
| `display.symbols` | `true` | Show APRS symbols |
| `display.trails` | `true` | Show selected station trail |
| `display.tracking_calls` | `[]` | Saved tracking windows (up to 8) |
| `display.follow` | `false` | Follow selected station |

## groups

| Key | Example default | Meaning / UI label |
| --- | --- | --- |
| `groups.server` | `"ANSRVR"` | Announcement server |
| `groups.names` | `[]` | Saved announcement groups |
| `groups.keepalive` | `false` | Keep joined groups active |
| `groups.keepalive_minutes` | `120` | Group keepalive interval (minutes) |

## history

| Key | Example default | Meaning / UI label |
| --- | --- | --- |
| `history.enabled` | `true` | Record position history |
| `history.maximum_points` | `100000` | Total stored track points |
| `history.track_points` | `1000` | Points per displayed/exported track |

## imports

| Key | Example default | Meaning / UI label |
| --- | --- | --- |
| `imports.gpx_files` | `[]` | GPX files to display (one per line) |
| `imports.maximum_points` | `20000` | Maximum imported points |

## kiosk

| Key | Example default | Meaning / UI label |
| --- | --- | --- |
| `kiosk.server` | `"QRU"` | QRU query destination |
| `kiosk.enabled` | `false` | Answer queries to my callsign |
| `kiosk.entries` | `[]` | Kiosk entries: TOPIC=response, one per line |
| `kiosk.minimum_seconds` | `60` | Minimum reply interval per sender |

## legacyweather

| Key | Example default | Meaning / UI label |
| --- | --- | --- |
| `legacyweather.enabled` | `false` | Receive APRS NWS warnings |
| `legacyweather.filter` | `"g/NWS*"` | APRS-IS NWS warning filter |
| `legacyweather.shapefiles` | `[]` | Local WGS84 polygon .shp files (paired .dbf required) |
| `legacyweather.code_field` | `"UGC"` | DBF column with complete UGC code (e.g. FLZ041) |

## logging

| Key | Example default | Meaning / UI label |
| --- | --- | --- |
| `logging.csv` | `false` | Automatically log tracks to CSV |
| `logging.gpx` | `false` | Automatically log tracks to GPX |
| `logging.calls` | `[]` | Stations to log (empty: all received positions) |
| `logging.retention_days` | `30` | Automatic track file retention (days) |

## messaging

| Key | Example default | Meaning / UI label |
| --- | --- | --- |
| `messaging.auto_reply` | `false` | Reply while inactive |
| `messaging.inactive_minutes` | `15` | Inactivity before auto reply (minutes) |
| `messaging.reply_minutes` | `30` | Minimum auto reply interval per station (minutes) |
| `messaging.reply_text` | `"Away from keyboard"` | Automatic reply text |
| `messaging.email_gateway` | `"EMAIL-2"` | Email gateway callsign |

## multitrack

| Key | Example default | Meaning / UI label |
| --- | --- | --- |
| `multitrack.views` | `[]` | Per-window overrides: CALL&#124;key=value&#124;key=value |

## offline

| Key | Example default | Meaning / UI label |
| --- | --- | --- |
| `offline.enabled` | `false` | Use downloaded base map only |
| `offline.maximum_tiles` | `1000` | Maximum tiles per download |
| `offline.maximum_megabytes` | `1024` | Offline tile storage budget (MB) |

## platforms

| Key | Example default | Meaning / UI label |
| --- | --- | --- |
| `platforms.entries` | `[]` | Platform overrides: destination-prefix=name |
| `platforms.file` | `""` | Optional APRS tocall definitions JSON file |

## qru

| Key | Example default | Meaning / UI label |
| --- | --- | --- |
| `qru.advertise` | `true` | Advertise location when QRU responder is enabled |
| `qru.route` | `"is"` | QRU advertisement route |
| `qru.interval_seconds` | `1800` | QRU advertisement interval (seconds) |

## queries

| Key | Example default | Meaning / UI label |
| --- | --- | --- |
| `queries.enabled` | `true` | Answer standard APRS queries |
| `queries.minimum_seconds` | `30` | Query reply cooldown per station |
| `queries.maximum_results` | `10` | Maximum stations in a query response |

## satellites

| Key | Example default | Meaning / UI label |
| --- | --- | --- |
| `satellites.enabled` | `false` | Show tracked satellites |
| `satellites.file` | `"data/satellites.tle"` | TLE file (relative to application folder) |
| `satellites.url` | `"https://celestrak.org/NORAD/elements/gp.php?GROUP=amateur&FORMAT=tle"` | TLE download URL |
| `satellites.names` | `[]` | Satellite names to track (empty: first 20) |
| `satellites.maximum_age_days` | `14` | Maximum TLE age (days) |
| `satellites.prediction_hours` | `24` | Pass prediction window (hours) |

## tactical

| Key | Example default | Meaning / UI label |
| --- | --- | --- |
| `tactical.paths` | `true` | Show selected station's received packet path |
| `tactical.path_age_seconds` | `1800` | Maximum path age (seconds) |
| `tactical.nicknames` | `[]` | Nicknames: CALLSIGN=Name, one per line |
| `tactical.labels` | `"selected"` | Map labels |
| `tactical.maximum_labels` | `100` | Maximum map labels |
| `tactical.label_speed` | `true` | Include reported speed |
| `tactical.label_altitude` | `true` | Include reported altitude |
| `tactical.label_weather` | `true` | Include received weather |
| `tactical.dead_reckoning` | `true` | Show estimated moving positions |
| `tactical.prediction_seconds` | `300` | Stop predictions after (seconds) |
| `tactical.phg` | `true` | Show selected station's estimated PHG range |
| `tactical.shapes` | `true` | Show received lines and areas |
| `tactical.warning_polygons` | `true` | Show weather warning boundaries |
| `tactical.direction_finding` | `true` | Show direction-finding reports |
| `tactical.df_age_seconds` | `1800` | Maximum DF report age (seconds) |
| `tactical.df_zero_radius_km` | `10.0` | S0 non-detection reference radius (km) |
| `tactical.df_measurements` | `[]` | Local DF bearings: name,lat,lon,bearing,range_km,width_degrees |
| `tactical.horizon` | `true` | Show selected station's estimated radio horizon |
| `tactical.ground_meters` | `0.0` | Ground elevation MSL for horizon estimate (m) |
| `tactical.receiver_height_meters` | `2.0` | Receiving antenna height above ground (m) |
| `tactical.dx_minimum_km` | `10.0` | Minimum direct-heard DX distance (km) |
| `tactical.dx_retention_days` | `30` | Retain direct-heard DX records (days) |
| `tactical.dx_query` | `false` | Answer directed ?DX queries |
| `tactical.dx_status` | `false` | Include furthest direct-heard DX in periodic status |
| `tactical.qru_enabled` | `false` | Serve configured QRU object groups |
| `tactical.qru_address` | `"QRU"` | QRU service address |
| `tactical.qru_radius_km` | `50` | Default QRU radius (km) |
| `tactical.qru_maximum_radius_km` | `500` | Maximum QRU radius (km) |
| `tactical.qru_maximum_objects` | `5` | Maximum QRU objects per query |
| `tactical.qru_position_age_seconds` | `1800` | Maximum requester position age (seconds) |
| `tactical.qru_spacing_seconds` | `5` | Spacing between QRU object replies (seconds) |

## telemetry

| Key | Example default | Meaning / UI label |
| --- | --- | --- |
| `telemetry.input_file` | `"data/telemetry.json"` | External telemetry JSON file |
| `telemetry.input_age_seconds` | `120` | Maximum external telemetry age (seconds) |
| `telemetry.enabled` | `false` | Transmit own telemetry |
| `telemetry.interval_seconds` | `600` | Telemetry interval (seconds) |
| `telemetry.definitions_seconds` | `3600` | Definition interval (seconds) |
| `telemetry.route` | `"is"` | Telemetry route |
| `telemetry.channels` | `["Rx&#124;pkts&#124;rx&#124;0&#124;1&#124;0", "Tx&#124;pkts&#124;tx&#124;0&#124;1&#124;0", "Sta&#124;count&#124;stations&#124;0&#124;1&#124;0", "Spd&#124;knots&#124;speed&#124;0&#124;1&#124;0", "Up&#124;min&#124;uptime&#124;0&#124;1&#124;0"]` | Five channels: name&#124;unit&#124;source&#124;a&#124;b&#124;c; sources rx, tx, stations, speed, uptime, input:key, or a number |
| `telemetry.bits` | `"00000000"` | Eight digital bits |
| `telemetry.bit_names` | `["Bit1", "Bit2", "Bit3", "Bit4", "Bit5", "Bit6", "Bit7", "Bit8"]` | Eight digital channel names |
| `telemetry.title` | `"Station telemetry"` | Telemetry project title |

## weather

| Key | Example default | Meaning / UI label |
| --- | --- | --- |
| `weather.enabled` | `false` | Fetch weather alerts |
| `weather.alerts_url` | `"https://api.weather.gov/alerts/active?point={lat},{lon}"` | GeoJSON alert service URL |
| `weather.refresh_seconds` | `300` | Alert refresh interval (seconds) |
| `weather.radar_enabled` | `false` | Show radar overlay |
| `weather.radar_url` | `""` | Radar XYZ tile URL |
| `weather.radar_opacity` | `0.6` | Radar opacity |

## weatherstation

| Key | Example default | Meaning / UI label |
| --- | --- | --- |
| `weatherstation.enabled` | `false` | Transmit local weather |
| `weatherstation.source` | `"file"` | Weather input |
| `weatherstation.port` | `""` | Named weather port |
| `weatherstation.file` | `"data/weather.json"` | Weather data file (JSON, APRS or wxnow.txt) |
| `weatherstation.maximum_age_seconds` | `600` | Maximum weather input age (seconds) |
| `weatherstation.interval_seconds` | `600` | Weather transmit interval (seconds) |
| `weatherstation.route` | `"is"` | Weather outputs |
| `weatherstation.object` | `""` | Weather object name (empty: own station) |
