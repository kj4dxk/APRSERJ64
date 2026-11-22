Configuration policy

- User-facing service addresses, map sources, limits, feature preferences and APRS
  server presets belong in portable settings.toml. Do not embed runtime endpoints
  in UI or connection code. Bundled resources may seed first-run defaults only.
- Preserve configured values and edited server lists when filling missing defaults.
- Keep defaults aligned with APRSERJa where the same feature exists: pdeffenb
  raster maps enabled, overlays/underlays off, no cloud, analytics or paid services.
- North-up remains enforced with no rotation preference, as explicitly requested.
- Beaconing defaults enabled; transmit only through a verified connection using
  the selected position source. Never send a cached offline queue of old positions.
- Never commit local callsigns/passcodes, user settings, chat data, caches or logs.
- Keep implementation notes in documentation, not the application UI.

- Keep serial/TCP ports, protocol commands, paths, routing rules and rate limits
  editable in TOML. Hardware profiles are unverified until tested on the device.
- Never send Internet q constructs as RF paths. Keep IGate loop prevention and
  duplicate suppression in the shared core. Keep local demo isolated from RF.

- User clarified that ready-to-use defaults take priority over copying blank
  APRSIS32 RF paths: seed WIDE1-1,WIDE2-1 beacon paths, WIDE1-1 message paths and
  the WIDE1-1= fill-in rule. Digipeating activates through its explicit switch.
- New completed port setup defaults enabled. Unconfigured saved templates stay
  disabled. Never overwrite an existing user's paths, rules or enable choices.
- Keep port wiring edits transactional: maintain references across renames, never
  broaden routes when deleting their last target, and distinguish live status
  from an unapplied draft. Persist all functional choices in settings.toml.

- Explicit user Disconnect must persist across restarts until Save & connect. Unexpected disconnections must retry automatically. A test login must not clear a deliberate disconnect or send beacons.
