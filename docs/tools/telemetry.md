# Telemetry and sensor values

## View a station's telemetry

1. Open **Tools → Telemetry charts** and enter the station callsign.
2. Select an analog channel and inspect its samples, labels and units.
3. Check whether the station has sent `PARM`, `UNIT`, `EQNS` and `BITS` definitions.

Without definitions, a raw value is not automatically a temperature, voltage or percentage. Equations use `a × value² + b × value + c`. Digital bit values and bit-sense definitions are separate.

APRSIS64 receives classic `T#` samples, base91 comment telemetry and supported Mic-E hex telemetry. Base91's larger raw range does not expand the classic outbound 8-bit sample range.

## Send your own telemetry

Open **Settings → Telemetry**. Configure five channels, eight bit names/bits, a title, sample interval, definition interval and route. Enable transmission when the values and definitions are ready.

Each channel entry has this form:

```text
name|unit|source|a|b|c
```

Sources include `rx`, `tx`, `stations`, `speed`, `uptime`, a number, or `input:key`. For example, `Supply|volts|input:supply|0|0.1|0` converts raw 123 into 12.3 V through the transmitted equation. The values used by the classic outbound encoder must fit 0–255.

## Supply external values

Write a fresh JSON object to the configured telemetry input file:

```json
{"supply":123,"temperature":20}
```

Use `input:supply` and `input:temperature` in the channel sources. Set the input age limit and validate it in **Tools → Local sensors → Read telemetry input**. The file bridge does not automatically discover or poll arbitrary hardware sensors.

Changing a channel's meaning requires consistent definitions. Confirm received labels/equations with another implementation, not just the local graph. APRSIS32's old Windows Mobile battery/charging example is not the default telemetry channel layout in APRSIS64.
