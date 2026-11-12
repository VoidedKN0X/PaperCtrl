# PaperCtrl

**Scan paper into Paperless-ngx and browse your documents.**

[![Latest release](https://img.shields.io/github/v/release/VoidedKN0X/PaperCtrl?label=download)](https://github.com/VoidedKN0X/PaperCtrl/releases/latest) ![Android 15+](https://img.shields.io/badge/Android-15%2B-3DDC84?logo=android&logoColor=white) [![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue)](LICENSE) ![Telemetry-free](https://img.shields.io/badge/telemetry-none-brightgreen) [![Sponsor](https://img.shields.io/badge/sponsor-GitHub-ea4aaa?logo=githubsponsors&logoColor=white)](https://github.com/sponsors/VoidedKN0X)

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
- **Page manager**: delete, reorder, rotate and split a document's pages on your server, from page thumbnails drawn on the phone (what's offered follows your Paperless version)
- **Organise** tags, correspondents, document types, storage paths, custom fields and saved views, and restore documents from the trash, with your library's statistics at the top
- **Notifications** when uploads finish or fail, a daily inbox reminder, and push from Paperless through **[ntfy](https://f-droid.org/packages/io.heckel.ntfy/)** and your own ntfy server
- **Widget, quick settings tile and shortcuts** to start a scan in one tap
- **Several servers and accounts**, two-factor sign-in, self-signed certificates you confirm by fingerprint, and an optional home-network address you switch to yourself (PaperCtrl never changes addresses on its own)
- **Privacy**: your password is never saved (only an API token, encrypted on the phone), an optional [app lock](#app-lock) with its own PIN and optional fingerprint unlock (your server logins stay encrypted while the app is locked), and **Hide in screenshots** and **Hide in recent apps** switches

## Requirements

- Android 15 or newer, on a 64-bit ARM phone (nearly every phone from the last several years)
- A recent Paperless-ngx server (PaperCtrl tells you if yours is too old)
- **Google-free and de-Googled:** no Google Play services, Firebase or other Google services, and the app never contacts Google. It works the same on de-Googled phones (GrapheneOS, CalyxOS, /e/OS, LineageOS without Google apps).
- Optional: the [ntfy](https://f-droid.org/packages/io.heckel.ntfy/) app, for push from Paperless

## Install

1. Open the [Releases page](https://github.com/VoidedKN0X/PaperCtrl/releases/latest) on your phone, scroll down to **Assets** and tap the file that ends in `.apk` (for example `PaperCtrl-v0.7.3.apk`). The "Source code" zip and tar.gz files are not the app; you don't need them.
2. Open the downloaded file. Android may ask you to allow installing apps from your browser or file manager.
3. Updates install over the old version. Your settings and login are kept.

**Automatic updates:** on your phone, tap one of the buttons below.

- **Obtainium:** install [Obtainium](https://github.com/ImranR98/Obtainium) first. The button adds PaperCtrl and installs every new release for you.
- **F-Droid:** adds the [CtrlF-Droid repo](https://voidedkn0x.github.io/CtrlF-Droid/) to F-Droid, Droid-ify or Neo Store, with every Ctrl app and automatic updates.

<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/VoidedKN0X/PaperCtrl"><img src=".github/assets/badge_obtainium.png" alt="Get it on Obtainium" height="80"></a> <a href="https://voidedkn0x.github.io/CtrlF-Droid/"><img src=".github/assets/badge_ctrlf-droid.png" alt="Get it from our F-Droid repo" height="80"></a>

If the Obtainium button doesn't open the app, add `https://github.com/VoidedKN0X/PaperCtrl` in Obtainium by hand.

## Is it safe to install?

- **Open source:** all of PaperCtrl's code is in this repo, under the GPL-3.0 licence. You can read every line.
- **Built by GitHub, not on a personal computer:** every release on the Releases page is built by [the build workflow](.github/workflows/build.yml) on GitHub Actions, straight from the code in this repo.
- **Always signed with the same key:** Android refuses to install an update signed with a different key, so nobody else can replace PaperCtrl on your phone with their own version. The CtrlF-Droid repo only accepts APKs signed with this key. Its SHA-256 certificate fingerprint is:

  ```
  00:D1:B2:3E:CC:53:BA:51:98:B3:26:71:4D:1D:70:E8:3E:3A:BD:75:E2:E7:20:E6:90:B0:F0:6F:24:84:CB:6E
  ```

  Advanced users can compare it with [AppVerifier](https://github.com/soupslurpr/AppVerifier).
- **Check a download:** the release notes list the APK's SHA-256 checksum, so you can compare it with the file you downloaded. With the [GitHub CLI](https://cli.github.com/), `gh attestation verify <apk> -R VoidedKN0X/PaperCtrl` proves the APK was built by this repo's build workflow from the tagged commit. The signing-certificate fingerprint above stays the check that matters for updates.

**Permissions** PaperCtrl asks for, and why:

| Permission | What it's for |
| --- | --- |
| Internet | Talking to your own Paperless-ngx server |
| See network status | Waiting with uploads until you're online |
| Notifications | Upload results, the daily inbox reminder and push from Paperless (you can say no) |
| Run in the foreground (data sync) | Finishing an upload with a progress notification, even if you leave the app |
| Camera | The document scanner and reading ASN labels (asked the first time you scan) |
| Use biometrics | Unlocking the app lock with your fingerprint, if you turn that on |
| Hide other apps' floating windows | Hiding other apps' floating windows (chat heads, overlays) over the PIN screen, sign-in and "can't be undone" confirmations, so nothing can cover them. No prompt, and nothing changes on other screens |
| Prevent phone from sleeping | Keeping the phone awake while an upload or inbox check is running |
| Run at startup | Picking up waiting uploads and the daily inbox reminder again after the phone restarts |

The last two come with Android's background-work library, which runs the upload queue and the inbox reminder. Android also lists a permission named after PaperCtrl itself (`DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`): it only lets PaperCtrl's own parts talk to each other, and no other app can hold it. PaperCtrl has no permission for your contacts, location, microphone or files: shared files reach it only through Android's share sheet or file picker, one at a time.

PaperCtrl also only looks for one other app on your phone: the ntfy app, for push. See [Privacy](#privacy) for what it sends where.

## Getting started

1. Open PaperCtrl and enter your **Server address**, the one you'd open in a browser. If Paperless runs under a sub-path, include it (`example.com/paperless`). Optionally tap **Add a home-network address** (for example `192.168.1.20:8000`). PaperCtrl starts on the main address; switch to the home-network address yourself (see below).
2. Sign in with your **Username** and **Password**, or switch to **API token** if you use single sign-on (in the Paperless web app: your profile picture → My Profile). With two-factor authentication, tap **I use two-factor authentication** and enter the code.
3. Tap **Connect**. If your server uses a self-signed certificate, check the SHA-256 fingerprint and tap **Trust this certificate**.

Settings, more servers and accounts are under **You** (the profile picture at the top right of every tab).

**Main or home-network address:** if you saved both, pick the one to use under **You → Servers and accounts → Address in use** (or tap the address on the **You** page). PaperCtrl stays on that address until you change it; it never switches by itself when your Wi-Fi or network changes. When the server can't be reached, the Documents and Inbox tabs offer a button to try the other address.

## Push notifications with ntfy

PaperCtrl tells you about its own uploads by itself. To also hear about documents Paperless gets another way (email, the consume folder, the web app), let Paperless push them to your phone through your own [ntfy](https://ntfy.sh/) server. Push comes through the **ntfy** app only; other push apps can't be used. PaperCtrl's own notifications (finished and failed uploads, the daily inbox reminder) keep working with or without it.

1. Install [ntfy from F-Droid](https://f-droid.org/packages/io.heckel.ntfy/) on your phone and point it at your ntfy server. If ntfy isn't installed, PaperCtrl's push screen shows an **Install ntfy** button.
2. In PaperCtrl, open **Settings → Notifications → Push from Paperless (ntfy)** and turn it on. PaperCtrl gets its push address from ntfy and shows it.
3. In Paperless, go to **Workflows** and add one:
   - **Trigger:** Document Added (add Document Updated too if you like).
   - **Action:** Webhook. **URL:** PaperCtrl's push address. Turn on **Send as JSON**, leave **Use parameters** off.
   - **Body:**
     ```json
     {"event": "added", "id": "{{doc_id}}", "title": "{{doc_title}}", "correspondent": "{{correspondent}}"}
     ```
4. Save, and use **Send a test** in PaperCtrl to check the address works.

If your ntfy server is only on your home network, Paperless blocks webhooks to local addresses by default: set `PAPERLESS_WEBHOOKS_ALLOW_INTERNAL_REQUESTS=true` on the server, or use ntfy's public HTTPS address. Tapping a notification opens the document.

## App lock

Turn it on under your profile picture › **Settings > Privacy and security > App lock**. It's off until you turn it on.

- **Its own app PIN** of at least 6 digits, separate from your phone's screen lock. Very obvious PINs such as 111111 or 123456 are refused.
- **Unlock with fingerprint** (optional) only accepts strong biometrics, such as the fingerprint sensor, checked by the phone's secure hardware. Face unlock that only uses the camera isn't accepted. The PIN is still needed after the phone restarts and at least every 72 hours, and adding a new fingerprint to the phone turns fingerprint unlock off until you turn it on again.
- **Your server logins are encrypted with the PIN while PaperCtrl is locked.** The key only exists in memory while the app is unlocked and is wiped the moment it locks. The PIN itself is never stored: it's stretched with 600,000 rounds of PBKDF2 and mixed with a key held in the phone's secure hardware, so a copy of the app's files isn't enough to guess it on a computer.
- **Wrong PINs:** after 5 tries you wait 30 seconds, doubling up to 15 minutes. Restarting the app or the phone, or changing the clock, doesn't skip the wait.
- **Lock again:** as soon as you leave the app (the default), after 1, 5 or 15 minutes or 1 hour away, or only when the app restarts. Picking files or photos, sharing, and opening a document or link in another app don't count as leaving, unless you stay away for more than 5 minutes.
- **Change app PIN** asks for your current PIN first, and turning the app lock off asks for the PIN too.
- **Forgot your PIN?** It can't be recovered, because it encrypts your logins. The lock screen offers to remove every server from this phone, with its queued uploads and cached data; everything on your Paperless server stays.
- **Share to PaperCtrl** asks for the PIN before anything you share reaches the upload queue.
- **Viewed documents are deleted when PaperCtrl locks.** The copies kept on the phone for viewing, sharing or "Open with" are removed the moment the app locks, and downloaded again when you open a document after unlocking.
- **Notifications only show their title** while the app lock is on (for example "Document added"), never a file name or document title, even when the phone itself is unlocked. A push with an event PaperCtrl doesn't know shows the plain "Paperless" title then, never the pushed text.
- **The widget shows "PaperCtrl is locked"** instead of the inbox count while the app is locked, including once you've been away longer than the lock timeout. The Scan button stays and opens PaperCtrl behind the lock screen.
- **Background work while locked:** an upload that's already sending still finishes. Uploads that haven't started yet, the daily inbox reminder and the widget's inbox count wait until you unlock, because the login stays encrypted. Push from Paperless through ntfy keeps working, since it doesn't need your login.

Two more switches in the same section work with or without the app lock, and are both off until you turn them on:

- **Hide in screenshots**: screenshots and screen recordings show a blank screen (and so does the recent-apps view).
- **Hide in recent apps**: the recent-apps view shows a blank card instead of PaperCtrl. Screenshots still work.

Signing in and the lock and PIN screens are always kept out of screenshots and recordings, whatever these switches say. With the app lock on, the recent-apps view always shows a blank card.

While the lock, PIN or sign-in screen or a "can't be undone" confirmation is showing, other apps' floating windows (chat heads, overlays) are hidden so nothing can cover it, and apps that read the screen through accessibility can't see the PIN or lock screen; real accessibility tools such as screen readers still work, and password managers can still fill the sign-in screen.

## Privacy

**Telemetry-free.** PaperCtrl has no analytics, crash reporting, usage stats or tracking of any kind. It only talks to your own Paperless-ngx server. Scanning, edge detection and ASN reading all happen on the phone.

- **Servers:** only the Paperless-ngx servers you add (their main and home-network addresses). Push notifications come through your own ntfy server and the ntfy app, and **Send a test** posts to PaperCtrl's push address there.
- **Links:** the Install ntfy button and the Support, Report a bug, Source code and Licence rows in About open in your browser only when you tap them; the app itself doesn't contact those sites.
- **Notifications** on the lock screen only show their title when you hide sensitive notification content in Android's settings, never a file name or document title. With the app lock on, they only show their title everywhere.
- **No backups:** Android doesn't back up PaperCtrl's data or move it to a new phone.
- **No update check:** PaperCtrl never checks for updates by itself; Obtainium or the F-Droid client does that if you use one.

**Google-free.** No Google Play services, Firebase, ML Kit or other Google libraries: the scanner is fully on-device and open source. Nothing ever goes to Google. Every build checks its dependencies for them, and release builds write nothing about you to the phone's log.

## How PaperCtrl is made

PaperCtrl is a one-person project by VoidedKN0X (VKNXWorks). Much of the code is written with the help of AI (Claude, by Anthropic). I decide what the app does, review the changes and use the app myself. Every change is listed in the [changelog](CHANGELOG.md), and you can read every line of code here.

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

**Dependency verification:** every library and plugin the build downloads is checked against its SHA-256 checksum in `gradle/verification-metadata.xml`, and the build stops if one doesn't match. When you change a dependency, a plugin or the Gradle version, run the **Dependency verification metadata** workflow (Actions tab, **Run workflow**), decode the file it prints, check the diff and commit it together with the change.

## Support the project

PaperCtrl is free and open source. If it's useful to you, tap **Support PaperCtrl** under **About** on the **You** page (tap your profile picture) to sponsor development on [GitHub Sponsors](https://github.com/sponsors/VoidedKN0X). Found a problem or have an idea? Tap **Report a bug** in the same section, or [open an issue](https://github.com/VoidedKN0X/PaperCtrl/issues/new/choose) and pick **Bug report** or **Feature request**. Starring the repo helps too.

Found a security problem? Please don't open a public issue; see [SECURITY.md](SECURITY.md).

## The Ctrl series

Native Android apps for self-hosted services and everyday tools, all with the same Material You design.

| App | What it's for |
| --- | --- |
| [AdCtrl](https://github.com/VoidedKN0X/AdCtrl) | Control your AdGuard Home DNS server |
| [ArrCtrl](https://github.com/VoidedKN0X/ArrCtrl) | Radarr, Sonarr, Prowlarr, Bazarr, qBittorrent, autobrr and Cleanuparr in one app |
| [BeamCtrl](https://github.com/VoidedKN0X/BeamCtrl) | TV remote for phones with an IR blaster |
| [CallCtrl](https://github.com/VoidedKN0X/CallCtrl) | Phone app: dialer, call history and in-call screen |
| [CameraCtrl](https://github.com/VoidedKN0X/CameraCtrl) | Private camera with RAW (DNG) on every lens |
| [ChatCtrl](https://github.com/VoidedKN0X/ChatCtrl) | Matrix chat client for matrix.org and self-hosted Synapse |
| [ClockCtrl](https://github.com/VoidedKN0X/ClockCtrl) | Alarms, timers, stopwatch and world clock |
| [ContactCtrl](https://github.com/VoidedKN0X/ContactCtrl) | Contacts: browse, edit, import and export |
| [CraftyCtrl](https://github.com/VoidedKN0X/CraftyCtrl) | Manage Minecraft servers on Crafty Controller |
| [DateCtrl](https://github.com/VoidedKN0X/DateCtrl) | Calendar for the calendars on your phone and your accounts |
| [FilesCtrl](https://github.com/VoidedKN0X/FilesCtrl) | File manager with NFS, SMB, SFTP, FTP and WebDAV |
| [FrigateCtrl](https://github.com/VoidedKN0X/FrigateCtrl) | Watch and review your Frigate NVR cameras |
| [GalleryCtrl](https://github.com/VoidedKN0X/GalleryCtrl) | Private, offline gallery with an encrypted vault |
| [ImmichCtrl](https://github.com/VoidedKN0X/ImmichCtrl) | Photos and backup for your Immich server |
| [JellyCtrl](https://github.com/VoidedKN0X/JellyCtrl) | Movies, shows, music and Live TV from Jellyfin |
| [LauncherCtrl](https://github.com/VoidedKN0X/LauncherCtrl) | Minimal, private home screen |
| [LijnCtrl](https://github.com/VoidedKN0X/LijnCtrl) | Live De Lijn bus and tram departures |
| [LumenCtrl](https://github.com/VoidedKN0X/LumenCtrl) | Private, offline photo and video editor |
| [MailCtrl](https://github.com/VoidedKN0X/MailCtrl) | Email for Gmail and Proton Mail |
| [MapsCtrl](https://github.com/VoidedKN0X/MapsCtrl) | Private, offline maps and navigation |
| [MemoCtrl](https://github.com/VoidedKN0X/MemoCtrl) | Notes on your Memos server |
| [NaviCtrl](https://github.com/VoidedKN0X/NaviCtrl) | Music player for Navidrome servers |
| **PaperCtrl** (this app) | Scan and browse documents on Paperless-ngx |
| [PrintCtrl](https://github.com/VoidedKN0X/PrintCtrl) | Bambu Lab printers through Bambuddy, and Spoolman spools |
| [ProxCtrl](https://github.com/VoidedKN0X/ProxCtrl) | Manage your Proxmox VE server |
| [RecorderCtrl](https://github.com/VoidedKN0X/RecorderCtrl) | Private, offline voice recorder |
| [RideCtrl](https://github.com/VoidedKN0X/RideCtrl) | Offline maintenance, fuel and mod log for your vehicles |
| [SeerrCtrl](https://github.com/VoidedKN0X/SeerrCtrl) | Request movies and shows on Seerr / Jellyseerr |
| [ShutterCtrl](https://github.com/VoidedKN0X/ShutterCtrl) | Remote control and photo transfer for Canon cameras |
| [SoundCtrl](https://github.com/VoidedKN0X/SoundCtrl) | JBL headphones and speakers, without the account |
| [SumCtrl](https://github.com/VoidedKN0X/SumCtrl) | Offline calculator, unit converter and work tools |
| [SynapseCtrl](https://github.com/VoidedKN0X/SynapseCtrl) | Administer your Matrix Synapse server |
| [TalkCtrl](https://github.com/VoidedKN0X/TalkCtrl) | Cardo intercom settings, radio and updates |
| [TapoCtrl](https://github.com/VoidedKN0X/TapoCtrl) | Tapo plugs and bulbs on your home network |
| [TextCtrl](https://github.com/VoidedKN0X/TextCtrl) | Private SMS and MMS messaging |
| [TrueCtrl](https://github.com/VoidedKN0X/TrueCtrl) | Monitor and manage your TrueNAS server |
| [WakeCtrl](https://github.com/VoidedKN0X/WakeCtrl) | Keep the screen awake, per app or while charging |
| [WrtCtrl](https://github.com/VoidedKN0X/WrtCtrl) | Manage your OpenWrt router |

## License

PaperCtrl is licensed under the [GNU General Public License v3.0](LICENSE). © VKNXWorks

PaperCtrl is an independent app and isn't made by or affiliated with the Paperless-ngx project.

The bundled fonts are licensed under the SIL Open Font License 1.1: [Space Grotesk](https://github.com/floriankarsten/space-grotesk) (`app/src/main/assets/licenses/OFL-SpaceGrotesk.txt`) and [JetBrains Mono](https://github.com/JetBrains/JetBrainsMono) (`app/src/main/assets/licenses/OFL-JetBrainsMono.txt`). The SemiBold weight of Space Grotesk is a static instance made from the project's variable font.
