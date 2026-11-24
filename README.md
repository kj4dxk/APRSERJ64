# APRSERJ64

Portable Windows APRS mapping, conversations, beaconing, radio ports and operating tools.

[Android · APRSERJa](https://github.com/kj4dxk/APRSERJa) · [iPhone, iPad, and Mac · APRSERJi](https://github.com/kj4dxk/APRSERJi)

Prefer the classic Windows interface? [APRSIS32 downloads and manual](https://github.com/kj4dxk/APRSIS32).

## Get online

1. **[Download APRSERJ64.exe](https://github.com/kj4dxk/APRSERJ64/releases/latest/download/APRSERJ64.exe)** ([release notes](https://github.com/kj4dxk/APRSERJ64/releases)). Put it in a writable folder and run it; no installer or separate Java is needed.
2. Open **Settings → My position** and enter your actual fixed location or configure your GPS. Automatic beaconing requires a current GPS fix or a fixed position you confirm in Settings.
3. Click **your callsign at the top left**, enter **your own callsign-SSID** and APRS-IS passcode, select a server, and check the receive-area coordinates. No operator identity is preinstalled.
4. Select **Save & connect**. **APRS-IS connected and verified** means the server permits transmission. Incoming stations should appear; click one to open its chat.

Use your existing callsign's APRS-IS passcode if you have one. For requests, follow the [APRSIS32 passcode instructions](https://github.com/kj4dxk/APRSIS32/blob/main/docs/manual/doc-passcode.md): email the group owners with your licensed name and callsign, explaining which application you use. The legacy service specifies APRSISCE/32 users; do not claim to use a different application. Passcode `-1` permits receive-only access.

Your callsign and passcode are stored only in your local `settings.toml`. Keep that file and your data/logs private. Never distribute your configured folder as a clean download. [Full getting-started instructions](docs/start/quick-start.md).

## Maps and fallback

The default OSM-derived tiles come from `http://pdeffenb.homeip.net:82/raster/light/{z}/{x}/{y}.png`, independently hosted by the maintainer. This reduces requests to the OpenStreetMap community tile server. If the project server has problems, open **Settings → Map → OpenStreetMap**, then **Save**. Select **pdeffenb** there to switch back. These preset addresses are editable in your local `settings.toml`.

If the full map is blank, select **Compatibility map** on the map toolbar. It loads raster tiles through a separate renderer and shows station markers. You can also enable compatibility mode in Map settings. Use the community preset for normal interactive viewing, not bulk offline downloads. Map data © OpenStreetMap contributors. This is actively developed software: test the workflows and hardware you depend on before replacing an established installation, and [report problems](https://github.com/kj4dxk/APRSERJ64/issues) with the version and reproduction steps.

## User manual

**[Open the APRSERJ64 manual](docs/index.md)** — organized by task, with a quick start, operating guides, device setup, troubleshooting, a topic index and searchable configuration references.

- [First connection](docs/start/quick-start.md)
- [Moving from APRSIS32](docs/start/from-aprsis32.md)
- [Settings catalog](docs/reference/settings.md)
- [Build the searchable web and offline editions](docs/reference/maintaining.md)

The manual follows the current source and calls out differences and limitations. Releases use Lynn's `YYYY.MM.DD.HHmm` version convention, recorded in [version.txt](version.txt), with matching GitHub tags. See [versioning and release procedure](docs/reference/versioning.md). Documentation builds are available as GitHub Actions artifacts after the manual workflow runs.

## Development and licensing

See [README.txt](README.txt) for build instructions and historical implementation notes, and the release reports for version-specific validation. Application source is AGPL-3.0-only; the manual text is CC BY-SA 3.0 with [sources and attribution](docs/reference/credits.md).

## Windows mobile radio setup

Open **Ports → Add port**, then choose the protocol and connection in the port editor. **Simply(KISS)** expects a TNC already in KISS mode. The legacy **KISS** and Kenwood profiles retain their startup commands. Use **TEXT** for TNC2 monitor lines. KISS channels start at 0, and **AGW** uses the raw AGWPE radio number.

| Device or setup | Windows connection |
| --- | --- |
| USB KISS TNCs, including Raspberry Pi Pico TNC and NinoTNC | Choose **USB / COM serial**, the Windows COM device, and the baud rate configured in the firmware. |
| Mobilinkd or ESP32 firmware with Bluetooth Classic SPP | Pair in Windows, then choose **Bluetooth Classic SPP (COM)** and the outgoing COM port. |
| Mobilinkd TNC3/TNC4, BLE KISS handhelds such as BTECH UV-PRO, and ESP32 BLE KISS firmware | Enable the radio's KISS service. Choose **Simply(KISS) → Bluetooth LE KISS** and choose **Find Bluetooth LE devices** or enter its Bluetooth address. The bridge accepts standard BLE KISS and Nordic UART profiles. |
| Raspberry Pi or DigiPi, an ESP32 network TNC, or a computer running Dire Wolf with a USB sound card or AIOC | Use **TCP → Simply(KISS)** for KISS or **TCP → AGW** for a raw AGWPE endpoint. Configure audio levels and PTT in the software modem. **Find advertised KISS TNCs** fills the host and port from a LAN DNS-SD advertisement. |

These are protocol-compatible configurations. Consult the individual vendor or firmware guide for the physical connection, Bluetooth mode, baud rate, or TCP port. Windows bench tests use simulated TNCs and Dire Wolf over TCP. Physical USB, Bluetooth, audio level, PTT and RF performance depend on the device and firmware.

Each enabled port retries independently from one second up to one minute. **Disconnect port** saves that port disabled and cancels its retry. **Connect port** enables it again. **Disconnect APRS-IS** controls the Internet session. The port inspector shows live endpoints, channels, packet counts and radio packet write counts. In **Menu → Packet monitor → Open packet stream**, open separate receive and transmit windows for a named TNC or APRS-IS. Each stream has its own pause, filter, and export. **Read Mobilinkd audio and PTT settings** queries compatible KISS firmware. A reported gain or PTT style is a modem setting. A TX write counts a packet accepted by the host transport. The Messages view reports a decoded APRS acknowledgment from the recipient.

In **Settings → Beaconing → Beacon timing**, choose **GeniusBeaconing** for time, distance, heading, start/stop and forecast error. Choose **SmartBeaconing** for speed-based intervals and corner triggers, or **Time only** for a fixed interval. A GPS beacon requires a current fix and an eligible RF or APRS-IS output. A fixed beacon requires the confirmation checkbox under **Settings → My position**.

For local operation, configure GPS and a TNC in **Ports**, then use **Tools → Offline** for downloaded tiles, the bundled Boston OpenStreetMap region, or a local OpenStreetMap XML file. A local TCP TNC needs its LAN connection even when the Internet is unavailable. The bundled map is © OpenStreetMap contributors under the Open Database License 1.0. **Tools → Export a track** opens or saves the latest GPX for your callsign. GPX logging defaults on and respects a saved off setting.

Windows source builds also require the .NET 10 SDK to publish the self-contained WinRT BLE bridge. Set `APRSERJ64_DOTNET` to the SDK's `dotnet.exe` if it is outside PATH. Windows and Android pin the same shared-core revision for mobile radio.
