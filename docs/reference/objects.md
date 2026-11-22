# Object field reference

Fields apply to objects, items and companion stations. The default coordinates are examples: replace them before transmitting. See [object procedures](../operate/objects.md).

Each `[[objects]]` table defines one entry. The default column shows schema values; strings are shown without TOML quotation marks. An empty cell means an empty string. Arrays use TOML array syntax.

| Key | Schema default | Meaning / UI label |
| --- | --- | --- |
| `name` | (empty) | Name / companion callsign |
| `internet` | `true` | Send to APRS-IS |
| `radio` | `true` | Send to RF ports |
| `ports` | `[]` | RF ports (empty: all eligible ports) |
| `rf_path` | (empty) | Object RF path (blank: port beacon path) |
| `kind` | `object` | Report type |
| `enabled` | `true` | Enabled |
| `alive` | `true` | Alive (off sends a kill report) |
| `source` | `fixed` | Position source |
| `latitude` | `28.536725` | Latitude |
| `longitude` | `-81.242932` | Longitude |
| `altitude_meters` | `0` | Altitude (meters) |
| `course` | `0` | Course (degrees) |
| `speed_knots` | `0` | Speed (knots) |
| `symbol_table` | `/` | Symbol table / overlay |
| `symbol` | `-` | Symbol |
| `comment` | (empty) | Comment |
| `interval_seconds` | `1800` | Maximum interval (seconds) |
| `minimum_seconds` | `30` | Minimum interval (seconds) |
| `time_only` | `false` | Use time only |
| `start_stop` | `false` | Beacon on start / stop |
| `heading_degrees` | `30` | Heading change (degrees) |
| `forecast_error_meters` | `160.9344` | Forecast error (meters) |
| `distance_meters` | `1609.344` | Maximum distance (meters) |
| `qru_group` | (empty) | QRU group (blank: not queryable) |
| `query_only` | `false` | Transmit only when queried |
| `shape` | `point` | Object geometry |
| `vertices` | `[]` | Line/polygon vertices: latitude,longitude per line |
| `line_style` | `j` | Multiline style (a–l) |
| `area_type` | `4` | Area type |
| `area_color` | `4` | Area color (0–15) |
| `area_latitude_code` | `10` | Area north/south extent code (0–99) |
| `area_longitude_code` | `10` | Area east/west extent code (0–99) |

For types and quoting see [configuration files](files.md). Avoid duplicate table/key declarations.
