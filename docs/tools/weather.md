# Weather observations and warnings

## Read received weather

Open **Tools → Weather**. APRS observations are decoded into temperatures, wind, humidity, pressure and rainfall where supplied. Missing values remain missing. A weather object keeps its own identity rather than replacing its owner's observation.

Received weather, Internet alert fetching, radar overlays, APRS NWS warning messages and your own weather transmitter are separate features. Enable only the ones you intend to use.

## Transmit local weather

1. Arrange for your collector to provide fresh SI JSON, an APRS weather packet, supported Peet/Ultimeter text, or `wxnow.txt`; alternatively configure a named **Weather** port.
2. Open **Settings → Weatherstation** and choose `file` or `port` as the input source.
3. Set the filename or port name, maximum observation age, interval, output route and optional weather object name.
4. Make sure your station has a valid current position. This version's weather object uses that position, not a separate weather-object coordinate field.
5. Open **Tools → Local sensors → Read weather input**. Resolve format/freshness problems before enabling transmission.
6. Enable **Transmit local weather**, Save and inspect the resulting packet.

Example JSON input, with optional values omitted when unknown:

```json
{"temperature_c":20.0,"wind_degrees":180,"wind_kph":16.1,"gust_kph":25.0,"humidity":50,"pressure_hpa":1013.2,"rain_hour_mm":0.0,"rain_day_mm":2.5}
```

The collector must keep the file fresh. Use the specified SI units; do not put Fahrenheit in `temperature_c` or mph in `wind_kph`.

## Use wxnow.txt

The bridge expects a UTC date/time line followed by a weather line, for example:

```text
Nov 21 2026 12:00
180/010g020t068r000p010P020h50b10132
```

This is a format example and will be stale if used literally later. The producer must write the current observation time. APRSIS64 checks both file freshness and the embedded timestamp. The weather line uses APRS weather units; `r`, `p` and `P` represent different rainfall periods and must not be interchanged.

For Tempest, Davis or another collector, use a compatible external program that emits a supported file. The APRSIS32 manual's Tempest workflow uses an external collector; it is not evidence of a built-in native Tempest driver here.

## Weather warnings and boundaries

For Internet alerts, enable **Settings → Weather** and confirm the alert URL/location. For APRS NWS messages, enable **Settings → APRS weather** and inspect its filter (seeded `g/NWS*`).

To add warning boundaries:

1. Open **Tools → Weather boundaries**.
2. Browse to a geographic SHP/DBF dataset or its ZIP, then **Import boundaries**.
3. Configure the DBF code field under **Settings → APRS weather**. It must contain complete zone/county codes that match the warnings.
4. Confirm that warnings have matching geometry and valid expiration times.

Projected datasets are rejected; this version does not reproject them. Imported files are copied into the portable data area. See [compatibility limits](../reference/compatibility.md).
