# Messages and conversations

## Start a conversation

1. Select a station on the map or in the station list. The right panel opens that callsign's conversation.
2. To address a station not shown on the map, use **Chats**, enter **Callsign-SSID**, and select **Chat**.
3. Check the identity shown after **As**.
4. Leave **Delivery: Automatic** for normal use. Open it to choose Radio only / Internet only or change acknowledgment requests.
5. Enter your message and select **Send**, or press **Ctrl+Enter**.

Chats and their outgoing queue are stored in `data/chats.sqlite` and survive restarts. Conversations are scoped to the local account; switching your callsign does not merge histories from different identities.

Unsent drafts stay with each callsign while switching conversations during the current session. Drafts are not saved across app restarts. Sent/received messages and the outgoing queue are saved.

You can send another message while earlier messages are queued, waiting for acknowledgment or finished. Each message has its own delivery state and retry history. New messages take priority over eligible retries; normal transmission pacing still applies.

## Understand routes

| Route | What APRSIS64 does |
| --- | --- |
| Automatic (stored as `best`) | Prefer a recently learned local-client route, then a suitable heard RF route; otherwise verified APRS-IS or an eligible RF fallback |
| Radio only (`rf`) | Use an eligible radio output rather than APRS-IS |
| Internet only (`is`) | Use the upstream verified APRS-IS connection |

A recently learned local client that disappears causes a local-send failure rather than silently redirecting that conversation. Per-conversation choices are saved. For a radio route, the relevant port must allow transmission and messages.

## Delivery states

The chat shows plain-language status on its own line, with the timestamp and delivery-attempt count below it. **Sent — waiting for acknowledgment** means the local send succeeded but remote receipt is not yet confirmed. **Delivered — acknowledgment received** confirms an acknowledgment came back. The stored state names below remain useful for diagnostics.

| State | Meaning and next action |
| --- | --- |
| QUEUED | Waiting for an eligible send attempt; check the route and connection |
| SENDING | An attempt is in progress |
| WAITING | Sent locally and waiting for ACK |
| ACKNOWLEDGED | Acknowledgment received for the message ID |
| SENT | No ACK was requested and the local send succeeded; remote receipt is unconfirmed |
| NO_ACK | Retry/age limit reached without acknowledgment |
| REJECTED | Remote rejection received |

Version 0.8.0 allows up to five attempts, with a 30-second eligibility interval and a 15-minute queue age limit. It does **not** reproduce APRSIS32's historical seven-retry/second-retry schedule. **Retry as new message** creates a new outgoing message for an exhausted/rejected entry. A received `?APRSM` requeues pending/unacknowledged messages for that requester, retaining their original IDs and history timestamps; it does not reopen acknowledged or rejected entries.

## Message length and special characters

An APRS message body is limited to 67 characters. APRSIS64 accepts up to 300 characters and splits long text into numbered messages. Each part is delivered independently. Use printable ASCII; `{`, `|` and `~` are reserved by the composer/packet format. A multipart exchange can arrive incompletely or out of order.

## Chats and history

Use **Chats** to return to the conversation list, then reopen a callsign. Read status and retry state persist with the database. Close the program before copying its folder for a consistent backup.

For a missing ACK, follow [message troubleshooting](../help/troubleshooting.md#message-stays-queued-or-no_ack). For group announcements, bulletins and email, see [messaging services](services.md).
