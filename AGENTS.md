Configuration policy

- User-facing service addresses, map sources, limits, feature preferences and APRS
  server presets belong in portable settings.toml. Do not embed runtime endpoints
  in UI or connection code. Bundled resources may seed first-run defaults only.
- Preserve configured values and edited server lists when filling missing defaults.
- Keep defaults aligned with APRSISOSM where the same feature exists: pdeffenb
  raster maps enabled, overlays/underlays off, no cloud, analytics or paid services.
- North-up remains enforced with no rotation preference, as explicitly requested.
- Beaconing defaults enabled; transmit only through a verified connection using
  the selected position source. Never send a cached offline queue of old positions.
- Never commit local callsigns/passcodes, user settings, chat data, caches or logs.
- Keep implementation notes in documentation, not the application UI.
