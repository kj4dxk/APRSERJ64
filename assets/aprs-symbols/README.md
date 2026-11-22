# APRS symbol vectors

This set contains 188 standalone SVGs redrawn from the two standard APRSIS32 symbol sheets. Each table covers printable symbol codes from ASCII `!` (0x21) through `~` (0x7E).

| APRS table | Source bitmap | Directory |
| --- | --- | --- |
| Primary (`/`) | `mysymb.bmp` | [primary](primary/) |
| Alternate (`\`) | `mysymb2.BMP` | [alternate](alternate/) |

The filename is the lowercase hexadecimal ASCII code. For example, the primary car symbol `/>` is [primary/3e.svg](primary/3e.svg). An APRS overlay uses the corresponding alternate-table SVG as its base, with the overlay character drawn separately.

The redraws use a consistent 17 × 17 coordinate system. Shapes, lettering, and pictograms are drawn using SVG paths and geometric primitives. The files have transparent backgrounds and require no fonts or other external assets. The legacy artwork is the visual reference, including repeated placeholder symbols.
