# Port field reference

New-port schema defaults are shown below. Device presets can override them, particularly commands, enable state and paths. Saved unconfigured templates are disabled. See [port setup](../connect/ports.md) and [routing](../connect/routing.md).

Each `[[ports]]` table defines one entry. The default column shows schema values; strings are shown without TOML quotation marks. An empty cell means an empty string. Arrays use TOML array syntax.

| Key | Schema default | Meaning / UI label |
| --- | --- | --- |
| `name` | `New port` | Port name |
| `notes` | (empty) | Setup notes |
| `frequency_mhz` | (empty) | Radio frequency reminder (MHz) |
| `enabled` | `true` | Enabled |
| `protocol` | `Simply(KISS)` | Protocol |
| `transport` | `serial` | Connection |
| `device` | (empty) | Serial device (e.g. COM3) |
| `endpoint` | (empty) | TCP host:port |
| `baud` | `9600` | Serial baud |
| `serial_data_bits` | `8` | Data bits |
| `serial_parity` | `none` | Parity |
| `serial_stop_bits` | `1` | Stop bits |
| `channel` | `0` | KISS channel / AGW radio (zero based) |
| `provides_nmea` | `false` | Also provides NMEA |
| `transmit` | `true` | Transmit enabled |
| `rf_to_is` | `true` | Gate RF to Internet |
| `no_gate_me` | `false` | Do not IGate my own callsign |
| `is_to_rf` | `true` | Gate messages to local RF stations |
| `beacon` | `true` | Station beacon on this port |
| `beacon_path` | `WIDE1-1,WIDE2-1` | Beacon path |
| `message_path` | `WIDE1-1` | IGate message path |
| `messages` | `true` | Local messaging on this port |
| `objects` | `true` | Object beacons on this port |
| `digipeater` | `false` | Digipeater enabled |
| `digi_outputs` | `[]` | Digipeater output ports (empty: this port) |
| `digi_transforms` | `["WIDE1-1="]` | Digipeater transforms (one per line) |
| `gate_calls` | `[]` | Additional Internet source calls to RF |
| `gate_objects` | `[]` | Additional Internet object names to RF |
| `gate_filter` | (empty) | Additional IS-to-RF filter (e.g. r/28/-81/30 -b/TEST*) |
| `service_input` | `false` | Accept local service input |
| `service_peers` | `[]` | Allowed UDP source IPs (loopback is always allowed) |
| `service_heartbeat_seconds` | `20` | Local server heartbeat (seconds) |
| `service_max_clients` | `8` | Maximum local server clients |
| `service_call` | (empty) | CWOP station identifier |
| `agw_user` | (empty) | AGW username (empty: no authentication) |
| `agw_password` | (empty) | AGW password |
| `passall` | `false` | TNC PASSALL is enabled (blocks IGating) |
| `open_commands` | `[]` | Open commands (JSON strings; command&#124;response) |
| `close_commands` | `[]` | Close commands (JSON strings) |
| `command_prompt` | `cmd:` | TNC command prompt |
| `command_timeout_ms` | `3000` | Command response timeout (ms) |
| `command_delay_ms` | `100` | Command pacing (ms) |
| `reconnect_seconds` | `3` | Reconnect delay (seconds) |
| `transmit_per_minute` | `20` | Maximum transmissions per minute |
| `minimum_transmit_ms` | `1000` | Minimum transmission spacing (ms) |
| `kiss_txdelay` | `-1` | KISS TX delay (10 ms units; -1 leaves TNC unchanged) |
| `kiss_persistence` | `-1` | KISS persistence (-1 leaves unchanged) |
| `kiss_slot` | `-1` | KISS slot time (10 ms units) |
| `kiss_tail` | `-1` | KISS TX tail (10 ms units) |
| `kiss_duplex` | `-1` | KISS duplex (0/1; -1 leaves unchanged) |

For types and quoting see [configuration files](files.md). Avoid duplicate table/key declarations.
