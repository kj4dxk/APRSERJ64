# FT8, JS8Call and JT65 log import

**Goal:** display stations decoded by your digital-mode program and optionally publish APRS objects for them. APRSIS64 reads logs; it does not decode audio or run the digital-mode radio software.

## Configure the importer in 0.8.0

The 0.8.0 Settings window has a **Digital** tab, but its field selection is incomplete and the tab may be empty. Use `settings.toml` for this version:

1. Close APRSIS64 and copy `settings.toml` as a backup.
2. Find its existing `[digital]` table; edit it rather than adding a duplicate table.
3. Set the file path(s), enable importing and initially leave transmission off.
4. Save and restart. Check for imported stations with `-FT`, `-JS` or `-JT` names for ordinary-length calls.

```toml
[digital]
enabled = true
transmit = false
ft8_file = 'C:\Radio\WSJT-X\ALL.TXT'
js8_file = ''
jt65_file = ''
maximum_age_seconds = 1800
interval_seconds = 600
maximum_objects = 50
route = 'is'
ports = []
```

Replace the illustrative path with the actual receive log. The supported inputs are WSJT-X FT8 `ALL.TXT`, JS8Call `ALL.TXT`, and JT65-HF CSV with its Date/Time/QRG/Exchange fields.

## Check the result

Imported locations come from explicit callsign/grid information in received log entries. A Maidenhead grid gives a cell center, not a precise GPS fix. Transmit records and replies without a location, such as `RR73`, do not create a location.

The importer scans a bounded tail of each file, excludes stale entries, and limits the number of current objects. Local display works without a live APRS-IS connection once the installation has a valid station identity. It does not reconstruct every fragmented digital-mode exchange.

## Enable optional object transmission

After checking the imported calls and locations, set `transmit = true`, choose `route = 'is'`, `'rf'` or `'both'`, and specify RF port names if needed. The station beacon enable switch must also permit this feature's output. Empty `ports` means all eligible RF ports, not none.

Intervals and age limits are separate: one controls retransmission; the other controls when a decoded station expires. The object ownership journal allows expiry cleanup across restarts. Changing a log file's modification time does not make old observations current.

See [routing](../connect/routing.md) and [configuration editing](../reference/files.md).
