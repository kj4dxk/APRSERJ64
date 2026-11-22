# APRS symbol vectors

This set contains 188 standalone SVGs traced from the two standard APRSIS32 symbol sheets. Each table covers printable symbol codes from ASCII `!` (0x21) through `~` (0x7E).

| APRS table | Source bitmap | Directory |
| --- | --- | --- |
| Primary (`/`) | `mysymb.bmp` | [primary](primary/) |
| Alternate (`\`) | `mysymb2.BMP` | [alternate](alternate/) |

The filename is the lowercase hexadecimal ASCII code. For example, the primary car symbol `/>` is [primary/3e.svg](primary/3e.svg). An APRS overlay uses the corresponding alternate-table SVG as its base, with the overlay character drawn separately.

The SVGs use the legacy renderer's 17 × 17 crop and white transparency key. Each color is drawn as filled vector contours, with lightly rounded exposed corners. The files can render at any size and have no external dependencies.
