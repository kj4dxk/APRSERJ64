# Groups, bulletins, email and automatic replies

These services use APRS messages. A locally queued command is not proof that a remote service processed it.

## Join an announcement group

1. Open **Tools → Group messages**.
2. Check the configured group server in **Settings → Group messages**.
3. Enter the group name and use the join/list/announcement controls for the action you want.
4. Inspect the server's reply and the displayed membership evidence.

`J GROUP` requests a quiet join, `L` requests your memberships, `U GROUP` requests a leave, and `CQ GROUP text` sends an announcement and can also join the group. These are server commands, not guarantees of delivery. Group names and keepalive preferences belong in Settings.

After a new list request, previously confirmed entries become provisional until a matching reply arrives. Missing or delayed replies are not treated as proof of removal. Explicit server removal replies update the record. Read the confirmation timestamp as well as the state.

## Send or schedule a bulletin

Use **Tools → Bulletins** to review received bulletins and compose/schedule one. Choose its slot, group, interval, route and text. Scheduled entries require **Settings → Bulletins → Transmit saved bulletins** to be enabled. Their encoded file form is:

```text
slot|group|seconds|is/rf/both|text
```

For example, `1|TEST|600|is|Exercise starts at 1400 UTC` is an example entry, not a command to transmit. Bulletins are group information, not individually acknowledged chat messages.

## Send an email through a gateway

Open **Tools → Send APRS email**, check the gateway callsign, enter an address and short text, and review the number of gateway messages before selecting **Send email**. Gateway replies appear in its chat. Long text creates separate numbered gateway emails; it is not reassembled into one long email by APRSIS64.

The gateway address is configurable in **Settings → Messages**. Gateway policy and availability are external to APRSIS64; an APRS ACK is not an email delivery receipt.

## Automatic reply and kiosk

**Settings → Messages** controls inactivity-based automatic replies, their text and the per-station reply interval. The app avoids automatically replying to queries and its own automatic-reply form.

**Settings → Unattended operation** configures the information kiosk's command/reply entries. Keep responses short enough for APRS messages. Test a command with a second station and inspect the chat and packet records.

See also: [messages](messages.md), [queries and QRU](../reference/queries.md).
