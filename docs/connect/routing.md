# Wire beacons, messages, IGates and digipeaters

## Keep these operations separate

| Operation | Direction | Purpose |
| --- | --- | --- |
| Beacon | Application → IS and/or RF | Publish your station or object |
| Message | Application/peer → selected route | Deliver a chat, reply or ACK |
| RF-to-IS IGate | RF → Internet | Upload eligible received radio packets |
| IS-to-RF IGate | Internet → RF | Deliver eligible traffic to radio stations |
| Digipeater | RF → RF | Repeat matching path requests |

The diagram shows these relationships; enable the specific route you intend. A GPS feeds location, not an RF packet stream.

## Set up an RF-to-IS IGate

1. Configure a working RF input port and a verified APRS-IS connection.
2. Enable that port's RF-to-IS route and Apply.
3. Confirm a received radio packet in **Menu → Packet monitor…**, then inspect the corresponding IS transmission.
4. Check that the original station and path are retained and that your IGate is identified in the appended q path.

Packets from Internet-origin third-party envelopes must not loop back into IS. PASSALL and non-APRS/non-UI frames are not suitable input for normal gating. A receive-only IGate uses `qAO`; a transmit-capable route uses `qAR`.

## Add IS-to-RF message delivery

Enable IS-to-RF and transmission on the intended radio. Normal message gating depends on recent local RF hearing, configured hop/time limits, Internet provenance and duplicate checks. An Internet message existing in the feed is not enough to send it on every radio.

Calls, object lists and `gate_filter` can subscribe additional traffic, but they remain subordinate to loop, path and eligibility checks. Start with message delivery and add explicit subscriptions only when needed. The [filter reference](../reference/filters.md) explains subscription syntax.

## Enable a fill-in digipeater

1. Select the receiving RF port.
2. Review **Digipeater** and its transform rules. The seeded fill-in rule is `WIDE1-1=`; it is inactive until digipeating is enabled.
3. Choose the output radio(s) through **Wire digipeater outputs**.
4. Inspect the path and output compatibility, then Apply.
5. Confirm a matching received request and the repeated packet in **Menu → Packet monitor…**.

A transform is `requested-alias=replacement`. The normal transformation inserts this station as a used hop; an empty replacement consumes that alias. This is not a global “repeat every packet” switch. APRSIS32's old global XML rules should not be pasted directly into this per-port configuration.

## Default paths and limits

Seeded RF paths are `WIDE1-1,WIDE2-1` for beacons and `WIDE1-1` for messages. Existing installations retain their edited values. **Radio limits** controls heard time, local hop limit, duplicate interval and memory bounds; each port also has output rate limits. Paths are operational choices that must fit the radio network being used.

## Stop or limit transmissions

- Disable **Settings → Beaconing → Beaconing enabled** to stop that beacon engine's station/object schedule.
- Disable a radio port's **Transmit enabled** to stop its physical output.
- Disable the particular IGate/digipeater route to stop forwarding while retaining reception.
- Disable the owning feature for scheduled bulletins, telemetry, weather or digital objects; do not treat the station beacon switch as a universal transmit switch.
- **Menu → Disconnect APRS-IS** stops upstream Internet traffic. It is not an RF master switch.

Local accepted-write counters are not remote reception confirmations. Use an independent receiver or a message ACK for end-to-end evidence.
