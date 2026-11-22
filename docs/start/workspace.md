# Learn the main window

The main window has three operating areas: the station list on the left, the map in the center, and chats on the right.

## Main toolbar

| Control | Purpose |
| --- | --- |
| Connection | Callsign, passcode, APRS-IS endpoint and receive-area setup |
| Settings | Saved feature, map, position and beacon preferences |
| Ports | Graphical device setup and routing workspace |
| Packets | Received/transmitted packet records and diagnostic views |
| Tools | Weather, telemetry, tracks, offline downloads and other operating tools |
| Transmit | Request a current station beacon when eligible |
| Local demo | Local demonstration, available while IS is stopped |
| Disconnect IS | Stop APRS-IS; separately configured device ports remain a different control |
| About | Version and source/license information |

## Select and follow a station

Use **Find station** or select a marker. **Type** cycles the station category, and **Sort** changes ordering. **Save view defaults** stores the list/view preferences. A filter can hide stations that are still being received; clear it when diagnosing an empty view.

Select a station and choose **Open tracking window** for an independent tracked view. Up to eight saved tracking calls are supported. Per-call map settings are covered under [maps](../operate/maps.md#tracking-windows).

## Read status correctly

The station list footer reports beacon source/detail, the last send time, and device RX/TX/drop counters. These answer different questions:

- Connection state: is the connection available and verified?
- Current position: is a valid location available for a beacon?
- TX: did the local output accept a packet?
- ACK in chat: did a remote station acknowledge that message?

Neither TX nor an active-looking wire guarantees over-the-air reception.

## Save versus Apply

Use **Save** in Settings. In Ports, edits are a draft until **Apply**. Closing an unapplied port draft can discard it; the running connection still uses the last saved configuration. Do not interpret the draft diagram as a live traffic display.

A change rejected because another editor has saved settings should be reloaded and applied again after review. This protects changes made in another open dialog.

Next: [Maps](../operate/maps.md) or [messages](../operate/messages.md).
