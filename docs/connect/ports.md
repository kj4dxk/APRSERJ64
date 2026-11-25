# Add and manage device ports

A **protocol** describes the bytes (NMEA, KISS, AGW, text monitor). A **connection** describes how those bytes travel (serial COM or TCP). A TCP KISS connection is still KISS, not APRS-IS.

## Add a port

1. Select **Ports → Add port**.
2. Choose a profile close to your hardware. Give the port a descriptive, unique name.
3. Select its serial device and baud rate, or enter its TCP host and port.
4. Review receive/transmit capabilities, routing switches and paths.
5. Complete the setup, inspect the draft diagram, then select **Apply**.
6. Check the saved connection's live status and counters. For NMEA, wait for a fix; for a radio, inspect actual decoded packets.

Serial discovery lists devices without opening each one. A manually entered COM port is supported. Completed setups default enabled; saved unconfigured templates stay disabled.

## Choose a protocol

| Protocol family | Use |
| --- | --- |
| NMEA | GPS sentences; no RF transmission |
| Simply(KISS), KISS | AX.25 UI frames through a KISS TNC; profile command sequences differ |
| AEA PK-88 (KISS) | PK-88 firmware that enters KISS immediately on `KISS ON` |
| AGW | TCP connection to an AGW-compatible packet engine; optional username/password and radio channel |
| KWD700(KISS), KWD700(RO+GPS) | Kenwood D700 KISS or receive-only monitor/GPS profile |
| KWD710(APRS), KWD710(Pkt) | Kenwood D710 APRS/packet command and monitor workflows |
| CONVerse, TEXT | Supported text-mode TNC/monitor input and output |
| FTM350v1.3(RO) | Receive-only Yaesu monitor profile |
| Weather | Read-only weather data input |
| Service protocols | [CWOP, local servers and UDP](local-services.md) |

A profile existing in the list is not proof that every firmware revision has been tested. Hardware-specific command/baud/mode settings still have to match the actual device. Bluetooth SPP can be used through its Windows outgoing COM port; BLE-only devices need an appropriate bridge.

## Understand and edit the diagram

Select a port to emphasize its wires. **Show all wires** displays the wider setup. The inspector holds connection details and routing switches. **Wire digipeater outputs** enters click-to-connect mode; select destinations and then **Done wiring**. **Object routes** has a separate output matrix.

Solid wires mean eligible under the draft settings. Dashed wires mean a configured route is blocked by a setting or missing rule. Live connection state and counters are separate. A draft wire is not an instruction that has already been applied.

## Change, rename or remove a port

Edit the draft, use **Undo/Redo** if needed, and select **Apply** when satisfied. Apply reloads configured connections, so an edited device may reconnect. Renames update references. Deleting the last selected object/digital RF destination disables that output rather than widening it to all ports.

Changing a protocol restores that profile's protocol/routing defaults while retaining connection identity fields for review. Inspect the resulting wires before Apply. Do not assume an old digipeater route remains valid for a receive-only replacement profile.

## Command sequences and limits

### AEA PK-88

Choose **AEA PK-88 (KISS)** for later firmware with the behavior described in Appendix F. Use the Windows COM port, match the PK-88's terminal baud rate, and use KISS channel 0. APRSERJ64 opens serial ports with 8 data bits, no parity and one stop bit. The serial baud rate is separate from the radio packet rate.

The profile sends three Ctrl+C bytes together to recover command mode when reconnecting, checks for a prompt, then sends `KISS ON`. Later firmware switches to binary KISS immediately. Incoming RF traffic can interrupt the escape sequence, so retry during a quiet interval. If you get a timeout, check the COM port, RS-232 cable, terminal baud and format, and whether the PK-88 responds to a terminal at those settings. If needed, finish terminal autobaud setup and close the terminal before opening the port in APRSERJ64.

For earlier firmware that requires a separate `HOST ON`, enter KISS using the procedure under **KISS** in AEA's command summary, then close the terminal and select **Simply(KISS)**. Use **Simply(KISS)** for a PK-88 that has already been put into KISS and should stay there. Check decoded packet counts to verify reception.

APRSIS32 offered similar generic options. **Simply KISS** accepted binary KISS directly and **KISS** allowed custom XML open and close commands. Its default command sequence targeted Tiny-2-style TNCs. APRSERJ64's PK-88 profile provides a starting sequence with editable commands in the port inspector.

[AEA PK-88 Operating Manual, Appendix F](https://www.dl0bn.de/dc7xj/Anleitungen/AEA%20PK-88%20Manual.pdf) · [AEA early firmware command summary](https://www.repeater-builder.com/aea/pdf/aea-pk-88-user-manual.pdf) · [APRSIS32 KISS port documentation](https://aprsisce.wikidot.com/port-kiss)

### Advanced port settings

Advanced port fields include open/close commands, expected response text, command delay/timeouts, KISS parameters and transmit rate limits. Command entries can use `command|expected-response`; escaped carriage returns belong inside quoted TOML strings. Start with the selected profile's values and change only what the device requires.

See [routing](routing.md), [port settings reference](../reference/ports.md), and [device troubleshooting](../help/troubleshooting.md#gps-or-radio-port-will-not-connect).
