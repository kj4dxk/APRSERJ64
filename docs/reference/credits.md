# Sources, credits and documentation license

This manual was written for APRSIS64's current controls, settings schema and implementation. Topic coverage and operating context draw on the [APRSISCE/32 manual maintained by Paul Deffenbaugh KJ4DXK](https://github.com/kj4dxk/APRSIS32), also available as the [published legacy manual](https://kj4dxk.github.io/APRSIS32/).

APRSISCE/32 was developed by Lynn W. Deffenbaugh, KJ4ERJ (Silent Key), Homeside Software Inc. GeniusBeaconing is associated with that work. APRS was created by Bob Bruninga, WB4APR (Silent Key); APRS is a registered trademark of TAPR. OpenStreetMap data is credited to its contributors; retain the map provider's required attribution. MapLibre and other dependencies retain their own licenses and credits.

## Legacy reading by subject

Location entry uses Google's [Open Location Code](https://github.com/google/open-location-code) Java library (Apache-2.0) for Plus Code decoding and recovery against a nearby reference. See [map navigation](../operate/maps.md#accepted-location-formats) for supported inputs and limitations.

| Subject | Legacy source |
| --- | --- |
| Account setup | [Passcode](https://github.com/kj4dxk/APRSIS32/blob/main/docs/manual/doc-passcode.md) |
| Radio setup | [Port configuration](https://github.com/kj4dxk/APRSIS32/blob/main/docs/manual/port-configuration.md) |
| Message delivery | [Message retries](https://github.com/kj4dxk/APRSIS32/blob/main/docs/manual/message-retries.md) |
| Objects | [QRU](https://github.com/kj4dxk/APRSIS32/blob/main/docs/manual/doc-qru.md) |
| Weather inputs | [Weather stations](https://github.com/kj4dxk/APRSIS32/blob/main/docs/manual/doc-weather-stations.md), [Tempest](https://github.com/kj4dxk/APRSIS32/blob/main/docs/manual/weather-tempest.md) |
| Position reporting | [GeniusBeaconing](https://github.com/kj4dxk/APRSIS32/blob/main/docs/manual/en-geniusbeaconing.md) |
| Radio relaying | [Digipeating](https://github.com/kj4dxk/APRSIS32/blob/main/docs/manual/doc-digipeating.md) |
| Measurements and requests | [Telemetry](https://github.com/kj4dxk/APRSIS32/blob/main/docs/manual/doc-telemetry.md), [queries](https://github.com/kj4dxk/APRSIS32/blob/main/docs/manual/doc-queries.md) |
| Orbital tracking | [Satellites](https://github.com/kj4dxk/APRSIS32/blob/main/docs/manual/doc-satellite-tracking.md) |

The legacy wiki archive identifies its documentation as **Creative Commons Attribution–ShareAlike 3.0**. The new manual text under `docs/`, including adaptations, is distributed under [CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0/), with the above attribution. Changes include rewritten procedures, task-based organization, APRSIS64 settings references and explicit version differences. This documentation license does not replace APRSIS64's AGPL-3.0 code license or third-party licenses.
