# PaperCtrl

**Scan paper into Paperless-ngx and browse your documents.**

[![Latest release](https://img.shields.io/github/v/release/VoidedKN0X/PaperCtrl?label=download)](https://github.com/VoidedKN0X/PaperCtrl/releases/latest) ![Android 13+](https://img.shields.io/badge/Android-13%2B-3DDC84?logo=android&logoColor=white) [![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue)](LICENSE) ![Telemetry-free](https://img.shields.io/badge/telemetry-none-brightgreen) [![Sponsor](https://img.shields.io/badge/sponsor-GitHub-ea4aaa?logo=githubsponsors&logoColor=white)](https://github.com/sponsors/VoidedKN0X)

PaperCtrl is an Android app for self-hosted [Paperless-ngx](https://docs.paperless-ngx.com/) servers: a document scanner that gets paper into Paperless in a few taps, and a fast document browser made for your phone.

Part of the [Ctrl series](#the-ctrl-series) by VKNXWorks.

<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/VoidedKN0X/PaperCtrl"><img src=".github/assets/badge_obtainium.png" alt="Get it on Obtainium" height="80"></a> <a href="https://voidedkn0x.github.io/CtrlF-Droid/"><img src=".github/assets/badge_ctrlf-droid.png" alt="Get it from our F-Droid repo" height="80"></a>

## Features

- **Scanner** with edge detection, auto-capture, multi-page documents, cropping and four filters, or **Add from gallery** for photos you already took. Built entirely on open-source libraries
- **Details before uploading**: title, correspondent, document type, storage path, tags and archive serial number (ASN), with ASN labels read by the camera
- **Upload queue** that works offline and uploads when your server is reachable
- **Share to PaperCtrl** from any app, for PDFs, images and other files Paperless accepts
- **Inbox** to sort new documents with swipes and Paperless' own suggestions
- **Documents** with full-text search, filters, saved views and bulk editing; the first page of your list and inbox stays visible offline
- **Document viewer** for the original and the searchable (OCR'd) version, with notes, history and share links
- **Organise** tags, correspondents, document types, storage paths, custom fields and saved views, and restore documents from the trash
- **Notifications** when uploads finish or fail, a daily inbox reminder, and push from Paperless through your own ntfy server (no Google services)
- **Widget, quick settings tile and shortcuts** to start a scan in one tap
- **Several servers and accounts**, two-factor sign-in, self-signed certificates you confirm by fingerprint, and an optional home-network address you switch to yourself (PaperCtrl never changes addresses on its own)
- **Privacy**: your password is never saved (only an API token, encrypted on the phone), an optional app lock with fingerprint, face or screen lock, optional screenshot blocking, and a blank preview in recent apps

## Screenshots

Coming soon.

## Requirements

- Android 13 or newer, on a 64-bit ARM phone (nearly every phone from the last several years)
- A recent Paperless-ngx server (PaperCtrl tells you if yours is too old)
- No Google Play services needed: PaperCtrl is Google-free (no Firebase, Play services or Google servers; emoji use your phone's own font), so it works on de-Googled phones

## Install

1. Download the latest `.apk` file from the [Releases page](https://github.com/VoidedKN0X/PaperCtrl/releases/latest).
2. Open it on your phone. Android may ask you to allow installing apps from your browser or file manager.
3. Updates install over the old version. Your settings and login are kept.

**Automatic updates:** on your phone, tap one of the buttons below.

- **Obtainium:** install [Obtainium](https://github.com/ImranR98/Obtainium) first. The button adds PaperCtrl and installs every new release for you.
- **F-Droid:** adds the [CtrlF-Droid repo](https://voidedkn0x.github.io/CtrlF-Droid/) to F-Droid, Droid-ify or Neo Store, with every Ctrl app and automatic updates.

<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/VoidedKN0X/PaperCtrl"><img src=".github/assets/badge_obtainium.png" alt="Get it on Obtainium" height="80"></a> <a href="https://voidedkn0x.github.io/CtrlF-Droid/"><img src=".github/assets/badge_ctrlf-droid.png" alt="Get it from our F-Droid repo" height="80"></a>

If the Obtainium button doesn't open the app, add `https://github.com/VoidedKN0X/PaperCtrl` in Obtainium by hand.

## Getting started

1. Open PaperCtrl and enter your **Server address**, the one you'd open in a browser. If Paperless runs under a sub-path, include it (`example.com/paperless`). Optionally tap **Add a home-network address** (for example `192.168.1.20:8000`). PaperCtrl starts on the main address; switch to the home-network address yourself (see below).
2. Sign in with your **Username** and **Password**, or switch to **API token** if you use single sign-on (in the Paperless web app: your profile picture → My Profile). With two-factor authentication, tap **I use two-factor authentication** and enter the code.
3. Tap **Connect**. If your server uses a self-signed certificate, check the SHA-256 fingerprint and tap **Trust this certificate**.

Settings, more servers and accounts are under **You** (the profile picture at the top right of every tab).

**Main or home-network address:** if you saved both, pick the one to use under **You → Servers and accounts → Address in use** (or tap the address on the **You** page). PaperCtrl stays on that address until you change it; it never switches by itself when your Wi-Fi or network changes. When the server can't be reached, the Documents and Inbox tabs offer a button to try the other address.

## Push notifications with ntfy

PaperCtrl tells you about its own uploads by itself. To also hear about documents Paperless gets another way (email, the consume folder, the web app), let Paperless push them to your phone through your own [ntfy](https://ntfy.sh/) server:

1. Install the ntfy app (from F-Droid) on your phone and point it at your ntfy server.
2. In PaperCtrl, open **Settings → Notifications → Push from Paperless (ntfy)** and turn it on. PaperCtrl shows its push address.
3. In Paperless, go to **Workflows** and add one:
   - **Trigger:** Document Added (add Document Updated too if you like).
   - **Action:** Webhook. **URL:** PaperCtrl's push address. Turn on **Send as JSON**, leave **Use parameters** off.
   - **Body:**
     ```json
     {"event": "added", "id": "{{doc_id}}", "title": "{{doc_title}}", "correspondent": "{{correspondent}}"}
     ```
4. Save, and use **Send a test** in PaperCtrl to check the address works.

If your ntfy server is only on your home network, Paperless blocks webhooks to local addresses by default: set `PAPERLESS_WEBHOOKS_ALLOW_INTERNAL_REQUESTS=true` on the server, or use ntfy's public HTTPS address. Tapping a notification opens the document.

## Privacy

**Telemetry-free.** PaperCtrl has no analytics, crash reporting, usage stats or tracking of any kind. It only talks to your own Paperless-ngx server. Push notifications come through your own ntfy server (or another UnifiedPush app), and **Send a test** posts to PaperCtrl's push address there. Scanning, edge detection and ASN reading all happen on the phone.

## Build from source

You need [Android Studio](https://developer.android.com/studio), or JDK 17 and the Android SDK.

1. Clone the repo:
   ```bash
   git clone https://github.com/VoidedKN0X/PaperCtrl.git
   cd PaperCtrl
   ```
2. Build the app:
   ```bash
   ./gradlew assembleDebug
   ```
   On Windows, use `gradlew.bat assembleDebug`.
3. The APK is in `app/build/outputs/apk/debug/`. Copy it to your phone and install it.

Or open the folder in Android Studio and press **Run** with your phone connected.

## Support the project

PaperCtrl is free and open source. If it's useful to you, tap **Support PaperCtrl** under **About** on the **You** page (tap your profile picture) to sponsor development on [GitHub Sponsors](https://github.com/sponsors/VoidedKN0X). Found a problem or have an idea? Tap **Report a bug** in the same section, or [open an issue](https://github.com/VoidedKN0X/PaperCtrl/issues). Starring the repo helps too.

## The Ctrl series

Native Android apps for self-hosted services and everyday tools, all with the same Material You design.

| App | What it's for |
| --- | --- |
| [ArrCtrl](https://github.com/VoidedKN0X/ArrCtrl) | Radarr, Sonarr, Prowlarr, Bazarr, qBittorrent, autobrr and Cleanuparr in one app |
| [BeamCtrl](https://github.com/VoidedKN0X/BeamCtrl) | TV remote for phones with an IR blaster |
| [CraftyCtrl](https://github.com/VoidedKN0X/CraftyCtrl) | Manage Minecraft servers on Crafty Controller |
| [ElementCtrl](https://github.com/VoidedKN0X/ElementCtrl) | Matrix chat client for matrix.org and self-hosted Synapse |
| [FilesCtrl](https://github.com/VoidedKN0X/FilesCtrl) | File manager with NFS, SMB, SFTP, FTP and WebDAV |
| [FrigateCtrl](https://github.com/VoidedKN0X/FrigateCtrl) | Watch and review your Frigate NVR cameras |
| [ImmichCtrl](https://github.com/VoidedKN0X/ImmichCtrl) | Photos and backup for your Immich server |
| [JellyCtrl](https://github.com/VoidedKN0X/JellyCtrl) | Movies, shows, music and Live TV from Jellyfin |
| [NaviCtrl](https://github.com/VoidedKN0X/NaviCtrl) | Music player for Navidrome servers |
| **PaperCtrl** (this app) | Scan and browse documents on Paperless-ngx |
| [PrintCtrl](https://github.com/VoidedKN0X/PrintCtrl) | Bambu Lab printers through Bambuddy, and Spoolman spools |
| [ProxCtrl](https://github.com/VoidedKN0X/ProxCtrl) | Manage your Proxmox VE server |
| [SeerrCtrl](https://github.com/VoidedKN0X/SeerrCtrl) | Request movies and shows on Seerr / Jellyseerr |
| [TrueCtrl](https://github.com/VoidedKN0X/TrueCtrl) | Monitor and manage your TrueNAS server |

## License

PaperCtrl is licensed under the [GNU General Public License v3.0](LICENSE). © VKNXWorks

The Manrope font is licensed under the SIL Open Font License; see `FONT-LICENSE-Manrope.txt`.
