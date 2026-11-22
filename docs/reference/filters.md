# APRS-IS and local filters

There are three different filtering decisions: what the server sends you, what you display, and what you permit onto RF. Changing one does not automatically change the others.

## Receive-area filter

Use **Settings → Receive area & filters**:

- `range`: receive around the connection/filter coordinates.
- `moving`: follow your last known APRS-IS position; a fresh published position is needed for meaningful movement.
- `custom`: use your explicit terms plus normal delivery to your own callsign.

Additional terms are separate from the left-hand station search/type controls. NWS and QRU preferences can add their own receive terms.

## Common subscription examples

| Term | Meaning |
| --- | --- |
| `r/28.5/-81.2/50` | Positions/objects near this center, radius in kilometers |
| `b/N0CALL-13` | Exact station identity |
| `p/N0` | Station prefix |
| `g/NWS*` | Messages addressed to matching warning recipients |
| `o/HOSP*` | Object names matching a pattern without spaces |
| `os/FIRE HQ` | Strict object name containing a space; place this term last |
| `d/DIGI` | Packets through a used digipeater |
| `e/IGATE` | Packets entered through a matching q-path entry station |
| `t/w` | Weather traffic |
| `-b/N0CALL-13` | Exclude this station from matching subscriptions |

Examples illustrate syntax, not a suggested global RF feed. The [APRS-IS filter specification](https://www.aprs-is.net/javAPRSFilter.aspx) describes server semantics. Normal client/IGate message delivery is separate from subscription exclusions.

## Local gate_filter

Each radio can have a `gate_filter` for additional IS-to-RF subscriptions. APRSERJ64 supports range/area, station/prefix/object, path/entry, recipient/destination, type, symbol, q, moving and friend terms. Local `D` checks the broader path and `+` marks a required term; these extensions must not be assumed to work on every upstream server.

Local includes are ORed, exclusions win, and required terms must also match; retain an ordinary include when using required terms. An empty local gate filter adds no subscription. It does not disable ordinary eligible message gating. Duplicate, provenance, path and port limits still apply.
