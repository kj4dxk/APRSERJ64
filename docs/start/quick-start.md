# Install and make your first connection

**Goal:** see live stations and establish a usable station identity. You need Windows x64, a writable folder, Internet access for APRS-IS and online tiles, and your own callsign/passcode if you will transmit.

## Install the portable program

1. Place `APRSIS64.exe` in a folder you can write to, such as a folder under Documents. No installer or separate Java installation is required.
2. Run the EXE. The first launch extracts its bundled runtime into an `app/` subfolder and creates settings and data folders beside it.
3. Open **Settings → My position**. Check the selected position source and coordinates **before connecting with a transmitting account**. The seeded fixed location is in Florida; it is not automatically your location.
4. Select **Settings → Beaconing** and review **Beaconing enabled**, **Beacon to APRS-IS** and **Beacon to RF ports**. Beaconing starts enabled by default. Select **Save** when finished.

A map opening at a particular place does not set your transmitting position. The map center, APRS-IS filter center and beacon position have distinct settings. See [position and beaconing](../operate/beacons.md).

## Connect to APRS-IS

1. Click **your callsign at the top left**, or choose **Menu → My callsign & passcode**. On first launch, missing credentials open this editor automatically.
2. Enter your **callsign with SSID**, such as your call followed by `-13`. Examples in this manual use `N0CALL`; replace them with your own identity.
3. Enter your APRS-IS passcode. It is stored as readable text in `settings.toml`. An existing passcode for your callsign works across APRS clients. For a request, follow the [APRSIS32 passcode instructions](https://github.com/kj4dxk/APRSIS32/blob/main/docs/manual/doc-passcode.md), providing your licensed name, callsign and the application you actually use. The legacy service specifies APRSISCE/32 users.
4. Choose a saved server or enter a custom host and port. The seeded endpoint is `rotate.aprs2.net:14580`.
5. Check the **Receive-area center** location and its decoded preview. Coordinates, Google Maps coordinate links and Plus Codes are accepted. The radius is under **Settings → Receive area & filters**. Saving credentials with **Save & connect** also enables automatic reconnection.
6. Select **Save & connect**. Wait for the connection state and station list to update.

**Success check:** incoming stations appear, and a transmitting login shows **APRS-IS connected and verified**. Verification means the server permits transmission; it does not confirm delivery of a particular packet. A working socket or a visible map alone does not prove the login is verified. [Connection troubleshooting](../help/troubleshooting.md#connected-but-not-transmitting) explains the difference.

## Receive-only use

Choose **Receive only** in **My callsign & passcode** for an unverified receive-only APRS-IS login. Also turn off **Settings → Beaconing → Beaconing enabled** if you do not want scheduled station/object beacons. Existing RF ports are separate from Internet verification: disable their transmit/routing switches if you want the entire installation to receive only.

**Menu → Disconnect APRS-IS** stops the Internet connection; it does not close every RF port. See [output controls](../connect/routing.md#stop-or-limit-transmissions).

## First operating check

1. Find a station in the left-hand list and select it. Its map marker and chat panel should correspond to that station.
2. Open **Menu → Packet monitor…** and confirm recent RX records.
3. If transmitting is intended, confirm your current position and then select **Beacon now** once.
4. Check the beacon status and TX record. These prove a local send attempt/accepted write, not reception by another station. A reply or independent receiving station provides stronger evidence.

## Local demo

When APRS-IS is stopped, **Menu → Practice with local demo** supplies local test data. It is useful for exploring the display and is isolated from real RF operation. Demo stations are not evidence of a successful public-network connection.

Next: [Learn the main window](workspace.md).

Missing or malformed credentials prompt for correction. If the server rejects a numeric passcode, the editor opens with an explanation; receiving may continue, but APRS-IS transmission requires verification. **How do I get a passcode?** shows the preserved wiki instructions and their APRSISCE/32-only request-service limitation. **Disconnect APRS-IS** stays off across restarts until you choose **Save & connect**. Unexpected network failures retry automatically.


**Test connection** checks the entered callsign and passcode with the selected server without saving settings, changing your normal connection preference, or sending beacons. Results distinguish authenticated, unverified/receive-only, and no server response. Editing the inputs clears the previous test result.

The passcode-help email address can be selected, copied, or clicked to compose a request with your callsign. **Copy request** includes recipient, subject and body. **Formatted draft** opens an .eml message with a bold name placeholder in compatible email apps. Replace the name placeholder before sending; the app never sends the email itself.
