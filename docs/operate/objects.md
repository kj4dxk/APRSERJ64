# Objects, repeaters and QRU

An object describes a place or feature owned by your station. It is different from moving your own station marker.

## Create a fixed object

1. Open **Settings → Objects & items → New**.
2. Enter a unique name, report kind, symbol, fixed coordinates and comment.
3. Review **Enabled**, **Alive**, interval and position source. New objects may be enabled by default, so finish their location/output choices before saving.
4. Select Internet and/or RF outputs. For RF, choose named ports or deliberately use all eligible ports.
5. Save and inspect the next eligible transmission in **Menu → Packet monitor…**.

Objects use names up to nine characters; items and companion-station reports have their own format/identity constraints. QRU group objects require a space-free name. Use `fixed` for a location, or `follow` when the feature should use your current station position.

## Remove an object from the network

Turn **Alive** off while leaving the object enabled long enough to send its kill reports. Removing the entry immediately from Settings cannot tell other stations to delete their cached copy. The beacon engine tracks up to three kill sends separated by at least 30 seconds and retains kill progress across restarts. Output eligibility still applies.

## Create a repeater/frequency object

Open **Tools → Repeater objects** and use **Frequency object**. Enter a name, MHz, tone, offset, range in miles and coordinates. The tone and offset fields use APRS formats such as `T123`, `Toff`, `+060` or `-060`. Select **Create object**.

This editor saves the object **disabled**. Open **Settings → Objects & items**, review its outputs and enable it when ready. A received frequency report displayed by the app does not automatically retune your radio.

## Draw lines, polygons and areas

In the object's fields choose geometry `line`, `polygon` or `area`. Lines/polygons use one `latitude,longitude` vertex per line in the editor. These shapes require fixed object reports. Area fields are encoded APRS area parameters; they are not all distances in meters. Check the displayed result and the [object settings reference](../reference/objects.md).

## Use QRU as a client

Open **Tools → Find nearby objects**. Request `INFO` to discover groups, then request the desired group, optionally with a radius. The returned objects are centered on the requester's known position, not the server's location. Send a fresh position first when the server needs one. Use the units shown/configured by the peer; do not assume every historical server uses the same unit convention.

## Offer QRU objects

1. Create enabled, alive object entries and assign a **QRU group**, such as `HOSP` or a repeater group.
2. Enable the QRU responder through **Settings → Tactical overlays** and configure limits. The **QRU server** settings tab controls advertisement fields.
3. Select **Transmit only when queried** if an object should not have normal periodic beacons. This is APRSIS64's explicit field; do not reproduce the old manual's zero-interval XML recipe.
4. Select eligible IS/RF outputs and save.
5. Test from a station with a fresh known position. Inspect replies and the requested objects.

Point responses can use an item inside a directed message. Full multiline geometry is sent as an ordinary object on an eligible Internet output, so the receiver's subscription affects its reception.

See [query reference](../reference/queries.md) for `?APRSO NAME` and standard station queries.
