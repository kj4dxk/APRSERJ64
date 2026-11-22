# Compatibility and limits

This manual targets APRSIS64 **0.8.0**. The APRSIS32 manual supplies topic coverage and operating context; it does not prove identical behavior. Procedures here follow the current Windows source.

## Differences that affect operation

- Configuration is portable TOML. There is no automatic APRSIS32 XML configuration importer.
- North stays up. There is no rotation setting.
- Message retries use APRSIS64's five-attempt/30-second eligibility/15-minute age policy; see [messages](../operate/messages.md).
- Digipeater transforms and output selection are configured per port. New profiles include useful paths, but existing user choices remain authoritative.
- Object query-only behavior has an explicit field. Removing a settings entry is different from sending a kill.
- The Digital settings tab is empty in this version. [Configure the digital importer in TOML](../tools/digital.md).
- The satellite UI says “30-second sampling”; the current calculation brackets at 10 seconds and refines crossings to approximately one second. See [satellites](../tools/satellites.md).
- Tracks exports stored observations; there is no interactive time-slider replay control in this version.
- Heard-list query responses are not a reproduction of every APRSIS32 I/G/D counter or display. See [queries](queries.md).
- A routing service URL is configurable, but that does not provide a complete driving/walking/cycling navigation interface.

## Integration boundaries

Physical serial devices, radio command modes, external gateways, RF propagation and every GUI sequence have not all been verified on hardware. The baseline 0.8.0 development checks reported 116 passing automated tests; this is not a blanket interoperability certificate.

Weather sensors use supported input files/ports; a WeatherFlow Tempest does not become a native USB device through this app. Projected shapefiles require conversion to the geographic coordinate format accepted by the importer. Satellite predictions depend on current orbital elements and accurate station location/time. Offline tiles depend on provider access, coverage and download limits.

Confirm the relevant [port profile](ports.md), [routing rules](../connect/routing.md), and packet monitor output before relying on a deployment. Packet acceptance locally, APRS-IS verification, network propagation and remote reception are separate observations.
