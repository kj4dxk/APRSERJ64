# Learn the main window

The main window has three operating areas: the station list on the left, the map in the center, and chats on the right.

## Main toolbar

The toolbar keeps **Connect / Connection**, **Beacon now**, **Settings** and **Menu** visible. Familiar map actions use icons with hover labels; actions that could be ambiguous retain words.

| Control | Purpose |
| --- | --- |
| Connect / Connection | Callsign, passcode, APRS-IS server and receive area |
| Beacon now | Request a current station beacon when an output is eligible |
| Settings | Searchable preferences, starting with My position |
| Menu → Station details | Information about the selected station |
| Menu → Weather reports / Download maps | Common operating tools |
| Menu → All tools… | Grouped, searchable tools including track exports and specialist features |
| Menu → Radio & GPS ports… | Graphical device setup and routing workspace |
| Menu → Packet monitor… | Received/transmitted packet records and diagnostics |
| Menu → Practice with local demo | Explore stations and messaging without sending to public APRS or RF |
| Menu → Disconnect APRS-IS / Stop practice | Stop the current IS/demo connection |
| Menu → About & license | Version, source and license information |

Settings and Tools group everyday tasks first. Use **Find a section** for terms such as GPS, map, messages, GPX or digital. Former section names also work in search. Select a result to open it; filtering the navigation does not discard your edits. Settings changes require **Save**.

## Select and follow a station

Use **Find station** or select a marker. **Show** opens a direct category choice, and **Sort by** chooses callsign, most recent or nearest ordering. The three-dot **Station list options** menu contains **Save as my starting view**. A filter can hide stations that are still being received; clear it when diagnosing an empty view.

Select a station and choose **Track selected station** for an independent tracked view. Up to eight saved tracking calls are supported. Per-call map settings are covered under [maps](../operate/maps.md#tracking-windows).

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
