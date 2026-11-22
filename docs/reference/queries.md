# Queries and QRU command reference

Send an addressed query as a normal APRS message to the station's callsign. A reply depends on that station's implementation, route, current state and rate limits.

| Query | APRSIS64 0.8.0 behavior |
| --- | --- |
| `?APRSP` | Current position report if available |
| `?APRSS` | Status packet and configured port summary |
| `?APRST`, `?PING?` | Trace/path information for the received request |
| `?ABOUT`, `?APRSV`, `?VER` | Software/version reply |
| `?APRSD` | Recently directly heard RF calls |
| `?APRSL` | Recently local RF calls, using the configured hop limit |
| `?APRSH CALL` | Recent RF hourly counts for the named call |
| `?APRSH` | Heard report for the requester |
| `?APRSH *` | Recently heard RF call list |
| `?APRSM` | Requeue pending/unacknowledged messages for the requester |
| `?APRSO`, `?APRSO NAME` | Request eligible owned objects; respects outputs and suppression |
| `?IGATE` | IGate/port/connection summary |
| `?CPU` | Java/runtime resource summary |
| `?DX` | Tactical direct-heard RF DX summary, if enabled |
| `?APRS?` addressed | Supported-query list |
| Unaddressed data-type `?APRS?` | General position request; geographic arguments are not implemented |

**Settings → Queries** controls ordinary replies, per-requester cooldown and result limits. DX and QRU have their own tactical settings. The default ordinary-query cooldown is 30 seconds: rapidly sending several different queries can suppress later replies. Do not diagnose a missing second reply before checking that limit.

APRSIS64's heard report is not the old manual's full `I+G+D+d+R` counter output. A named-object request is subject to eligibility and a per-object suppression interval; zero objects sent may be a valid result.

## QRU

Use `INFO` for available nearby groups, then `GROUP` or a group plus radius to request objects. Query the configured QRU address or the server directly as appropriate. The requester needs a fresh known position. See [QRU setup and use](../operate/objects.md#offer-qru-objects).
