# Changelog

## 0.3.0

PaperCtrl no longer switches between your main and home-network address on its own: you choose which one it uses. It is also fully Google-free now.

### Changed
- **You choose the address.** If you saved a home-network address next to the main one, pick which one PaperCtrl uses under **You → Servers and accounts → Address in use**, or tap the address on the **You** page. PaperCtrl stays on the address you pick until you change it, also when your Wi-Fi, mobile data or VPN changes.
- **After updating, PaperCtrl uses your main address.** Your home-network address is still saved; if you want to use it at home, pick it under **Address in use**.
- **New servers start on the main address.** The home-network address you add while signing in is checked once and saved, and you switch to it yourself.
- **"Can't reach the server" offers the other address.** When the Documents or Inbox tab can't reach Paperless (or is showing what was loaded last time) and the server has a second address, a bar says which address failed with a **Use home address** or **Use main address** button. Nothing changes until you tap it.
- **Servers and accounts** now says for each server with two addresses which one is in use, and the address row on the **You** page says "tap to change".
- **README** brought up to date: the feature list covers adding photos from the gallery, setting details and reading ASN labels before uploading, the offline document list, saved views and the trash in Organise, two-factor sign-in, the home-network address and the privacy features; there's a **Getting started** section, the usage stats section says scanner uploads are counted too, and a paragraph lists everything PaperCtrl connects to besides your server.

### Removed
- **Automatic address switching.** PaperCtrl no longer checks in the background whether the home-network address answers, and no longer watches for network changes to move between addresses.

### Privacy
- **Google-free.** PaperCtrl no longer asks Google Play services for an emoji font when it starts; emoji use your phone's own font. PaperCtrl uses no Google Play services, Firebase or Google servers.
- **Your token only goes to the address you picked.** Because PaperCtrl no longer tries the home-network address by itself, it never sends anything to that address unless you chose it.

## 0.2.0

The full app: everything PaperCtrl was planned to do is in this version.

### New
- **Document scanner.** The Scan tab opens the camera. PaperCtrl outlines the page live, straightens it and crops it. With **auto-capture** on, it takes the picture by itself once the page holds still; or tap the shutter. There's a flashlight button, and **Add from gallery** imports photos you already took.
- **Multi-page scans.** Keep scanning to add pages, then review them all in a grid. Long-press and drag a page to change the order. Tap a page to move its corners, rotate it, let PaperCtrl find the edges again, use the full photo, retake it or delete it.
- **Four filters:** Original, Enhanced, Greyscale and Black & white. **Use for all pages** applies one to the whole scan, and PaperCtrl remembers the last filter you used for the next scan.
- **PDF quality setting** (Compact 150 dpi, Standard 200 dpi, High 300 dpi) in **Settings → Scanning**.
- **Unfinished scans are kept.** If you leave the scanner, the Scan tab shows how many pages are waiting and lets you continue or discard them.
- **Upload with details.** Before a scan uploads you can set title, correspondent, document type, storage path, tags and the archive serial number (ASN). **Scan ASN label** reads a barcode or QR code with the camera; you can also take the next free ASN from your server. Scans go through the same upload queue as shared files.
- **Documents tab.** Your whole library as a list or a grid with thumbnails, loading more as you scroll. Search titles and text, titles only, full text (with highlighted matches and suggestions as you type) or ASN. Sort by created, added, title, correspondent, ASN or relevance.
- **Filters:** tags (any, all, or without), correspondent, document type, storage path, owner, custom fields, created and added dates, and "only in the inbox".
- **Saved views.** Your Paperless saved views appear as chips above the list, the ones shown on the dashboard or sidebar first. Tap one to see its documents.
- **Bulk actions.** Long-press a document to select several, then add or remove tags, set correspondent, document type or storage path, download them as a ZIP, rotate them, merge them into one new document, or delete them.
- **Offline list.** The first page of your documents and inbox is kept on the phone, so you still see something (with an "Offline" banner) when the server can't be reached.
- **Document screen** with five tabs:
  - **Preview:** the PDF with pinch to zoom and page thumbnails; switch between the searchable PDF and the original file. Files PaperCtrl can't show open in another app.
  - **Details:** edit title, created date, correspondent, document type, storage path, tags and ASN, plus custom fields of every type (text, long text, link, date, yes/no, whole number, number, money, choice and linked documents).
  - **Text:** the text Paperless read from the document, with find-in-text and match count.
  - **Notes:** read, add and delete notes.
  - **History:** what changed and who changed it (when the audit log is on in Paperless).
- **Document actions:** save to Downloads (Downloads/PaperCtrl), share the file, create share links with an expiry (1, 7, 30, 365 days or never) and revoke them, open in the browser, **More like this**, reprocess, change owner and who can view or edit, and delete (with how long it stays in the trash).
- **Inbox tab.** Every document with an inbox tag. **Swipe right** to mark it done (removes the inbox tags; **Undo** in the message puts them back). **Swipe left** for a quick edit sheet with Paperless' own suggestions as chips, and **Save and mark done**. Select several for bulk actions.
- **Organise tab.** Tags, correspondents, document types, storage paths, custom fields and saved views, each with how many documents use it. Add, edit (name, colour, inbox tag, automatic matching, path template, field type and choices) and delete them. Tap one to see its documents.
- **Trash.** Restore deleted documents or empty the trash, from the Organise tab.
- **Notifications:**
  - **Document added** and **Upload failed** notifications can each be turned off in **Settings → Notifications**. "Document added" now opens the document.
  - **Daily inbox reminder** around 9:00 when documents are waiting (off by default).
  - **Push from Paperless through ntfy.** For documents Paperless gets by email or from the consume folder: PaperCtrl registers with the ntfy app (or any UnifiedPush app) and shows a push address plus step-by-step instructions and the webhook body for a Paperless workflow. No Google services involved; see the README.
- **Home screen widget** with a Scan button and how many documents wait in the inbox.
- **Quick Settings tile** "Scan to Paperless".
- **App shortcuts:** long-press the PaperCtrl icon for Scan, Inbox and Search.
- **App lock** (Settings → Privacy): unlock with your fingerprint, face or screen lock when opening PaperCtrl, and again after a minute in the background.
- **Block screenshots** (Settings → Privacy): keeps documents out of screenshots and screen recordings.

### Changed
- The Scan tab now starts the camera scanner; **Upload files** is still there for PDFs and photos on the phone.
- Downloaded and shared document files are kept in the app's private cache and deleted when PaperCtrl starts again or when you remove the server.
- Removing a server also deletes its offline document list.

## 0.1.3

### Security
- **Removing a server now removes everything queued for it.** Before, documents still waiting to upload to a server you removed stayed on the phone (as "failed") with their file names and details. Now, removing a server deletes its waiting uploads, their files and their notifications straight away, and anything left over from a server removed with an earlier version is deleted the next time PaperCtrl starts.
- **Share to PaperCtrl only accepts files from other apps.** A share that points at PaperCtrl's own files (through a `file://` link or one of PaperCtrl's own components) is ignored, so no app can get PaperCtrl to upload your other queued documents or its settings to your server.
- **Size limits for shared files.** Files larger than 1 GB, more than 100 files in one share, or a file that would nearly fill your phone's storage are left out instead of being copied, and PaperCtrl tells you how many were left out.
- **Unfinished shares are deleted sooner.** If PaperCtrl was closed or stopped while you were looking at the share screen, the copies of the shared files are now deleted the next time the app starts, instead of after a day.
- **No file names on the lock screen.** Upload notifications ("Sending…", "Document added", "Upload failed") are marked private: when your phone hides sensitive notification content on the lock screen, they show only their title, never a file name, document title or Paperless' message.
- **Hidden from the recent-apps preview everywhere.** Before, only the sign-in screen was hidden, and leaving it showed the app again. Now the whole app and the share screen show a blank preview in recent apps, so document titles and file names in the queue don't show there.
- **Your home-network address is checked before it gets your token.** PaperCtrl only switches to the home-network address when Paperless itself answers there, not any device that happens to use that address on another network (a café's router, a hotel printer), so your token isn't sent to it.
- **Warning for an unencrypted home-network address.** Typing an `http://` home-network address now shows the same "Not encrypted (HTTP)" warning as the main address, and **You → Servers and accounts** marks a server whose home address isn't encrypted.
- **Plain HTTP can't be redirected to another server.** A server you allowed over HTTP can no longer pass a request on to a different address over HTTP; each hop must be an address you chose.
- **Usage stats stop at once when switched off.** Turning usage stats off now also throws away anything that was still waiting to be sent, so nothing from before can go out if you switch them on again later.
- **No document images saved to storage.** Images PaperCtrl loads (such as document thumbnails in coming updates) are kept in memory only, never written to the phone's storage.

### Changed
- When you remove a server, the confirmation now says that anything still waiting in its upload queue is deleted too.
- The "Nothing to upload" message now mentions that files over 1 GB can't be shared to PaperCtrl.
- **All Ctrl apps in the README.** The README's Ctrl series table now lists all 14 apps. New in the table: [ArrCtrl](https://github.com/VoidedKN0X/ArrCtrl) (Radarr, Sonarr, Prowlarr, Bazarr, qBittorrent, autobrr and Cleanuparr in one app), [PrintCtrl](https://github.com/VoidedKN0X/PrintCtrl) (Bambu Lab printers through Bambuddy, and Spoolman spools), [ProxCtrl](https://github.com/VoidedKN0X/ProxCtrl) (manage your Proxmox VE server) and [TrueCtrl](https://github.com/VoidedKN0X/TrueCtrl) (monitor and manage your TrueNAS server). Every app in the table is also in the [CtrlF-Droid repo](https://voidedkn0x.github.io/CtrlF-Droid/) for automatic updates. No change to the app itself.

## 0.1.2

### New
- **Optional anonymous usage stats.** After you connect to your server, PaperCtrl asks once whether you'd like to share anonymous usage stats. Nothing is sent unless you tap **Share stats**, and you can switch it on or off any time in **You → Settings → Usage stats**. The setting shows exactly what's shared.
- **What's shared:** which screens you open (for example "Inbox", "Scan", "Queue" or "Settings"), and for each upload whether it came from sharing or from **Upload files**, roughly how many pages it has (1, 2–5 or 6+) and what kind of file it is (PDF, image, combined images, text or other). Also the app version, Android version, phone model, language, phone or tablet, and which theme you use.
- **What's never shared:** your server address, username, password or token, and nothing about your documents: no titles, contents, tags, correspondents, document types, file names, search text or document IDs. There's no ID that follows you between sessions either: each session gets a new random ID, and stats from before and after you switch them off and on again aren't linked.
- **Where it goes:** to VKNXWorks' own self-hosted server (Aptabase), not to Google or any analytics company. Builds you make from source can't send stats at all, and the setting doesn't appear in them.

## 0.1.1

### Fixed
- **Connecting to Paperless-ngx 3 failed** with "There's no Paperless-ngx API at this address", even with the right address. Paperless 3 sends visitors of `/api/` on to its API documentation page, and PaperCtrl mistook that page for something other than Paperless. The connection check now asks an API endpoint that always answers directly, so servers behind a reverse proxy such as Nginx Proxy Manager connect normally.

### Changed
- Clearer connection errors: if the address forwards to a sign-in page of your reverse proxy (Authelia, Authentik…) instead of to Paperless, PaperCtrl says so and names where it was sent. When something other than Paperless answers, the message now shows what answered (status, content type, and Cloudflare if it's in between).
- A redirect from your proxy to HTTPS on the same address is followed automatically, and the HTTPS address is saved.

## 0.1.0

### New
- **Share to PaperCtrl:** share PDFs, photos, text and other files Paperless accepts (office documents need Tika on the server) from any app, one or many at once. PaperCtrl copies them straight away, so the sharing app can be closed.
- **Upload files from the Scan tab:** pick documents from your phone with the new **Upload files** button until the camera scanner arrives.
- **Combine images into one PDF:** when you share several images, they're merged into a single PDF in the order you shared them (on by default; turn it off to upload each image separately). Pages get their real paper size and stay small: a full-page photo is a few hundred KB.
- **Optional details before uploading:** title, date, correspondent, document type, tags, storage path and archive serial number (with a **Next free** button that asks the server for the next unused ASN). Pickers list what's on your server and are searchable. Leave the details folded away and tap **Quick upload** to let Paperless match everything itself.
- **Upload queue that works offline:** every upload waits in a queue that survives closing the app and restarting the phone, and goes out by itself once your server is reachable. If you have several servers, you choose which one each share goes to.
- **Queue screen** (Scan tab, or tap a notification) showing each document as waiting, uploading with progress, being processed by Paperless, added, or failed with Paperless' own reason (for example "It is a duplicate of document #12"). Retry, edit the details of anything not sent yet, open an added document in Paperless, or remove items.
- **Notifications:** a progress notification while documents upload and Paperless processes them, then "Document added" with its title, or "Upload failed" with the reason. PaperCtrl asks for notification permission the first time you share something.
- Files are deleted from the phone as soon as Paperless has added them; finished items leave the queue list after a week.
- **Connect to your Paperless-ngx server.** On first start PaperCtrl asks for your server's address, then signs in with your username and password, or with an API token. Use a token if you sign in to Paperless with single sign-on (SSO); the screen explains where to find it in the Paperless web app.
- **Two-factor authentication:** accounts with an authenticator app can sign in by entering the current code. PaperCtrl asks for it automatically when the server needs one.
- **Your password is never saved.** It's used once to get an API token from the server, and only the token is kept, encrypted with a key that never leaves this phone.
- **Self-signed certificates:** when your server's certificate isn't trusted by Android, PaperCtrl shows who it was issued to, its validity and its SHA-256 fingerprint, and only trusts that exact certificate once you confirm it. If the certificate later changes, PaperCtrl refuses to connect instead of quietly accepting it; adding the server again shows the new certificate with a warning.
- **Home-network address:** optionally add a second address (for example the server's LAN IP). PaperCtrl uses it whenever it answers and the main address everywhere else, and switches by itself when you join or leave Wi-Fi or a VPN. The You page shows which one is in use.
- **Several servers and accounts:** add more from You → Servers and accounts, switch between them with a tap, or remove one from the phone.
- **Clear connection errors:** separate explanations for an unreachable server, a rejected certificate, a wrong password or code, an address with no Paperless API (with a hint about sub-paths), and a Paperless version too old for the app.
- **You page** now shows your name, whether you're an administrator, the Paperless version and the server address in use.

### Security
- An address typed without `https://` is tried over HTTPS only; plain HTTP needs your explicit confirmation, and an HTTPS connection can never be redirected to HTTP.
- Requests that carry your password are never passed on to another server by a redirect, and your token is removed from any redirect that leaves the server.
- The sign-in screen is hidden from the recent-apps preview, since it can show your password or token.

## 0.0.1

The first PaperCtrl build: the foundation for the scanner and document browser that arrive in the next updates. It doesn't connect to a Paperless-ngx server yet.

### New
- **Four tabs** in a floating glass tab bar: Inbox, Documents, Scan and Organise. Each one explains what it will do; the features themselves come in the following updates. On tablets and unfolded foldables the tabs move to a side rail.
- **You page**, opened by tapping the profile picture in the top corner of any tab, with Settings, a link to support development on GitHub Sponsors, the source code and the bug tracker.
- **Appearance settings:** follow the system theme, or pick dark, light or pure black, and optionally use your wallpaper's colours.
- **App icon** in the Ctrl series keycap style, with a themed (monochrome) version for Android's themed icons.

### Security
- Nothing from the app is included in Android cloud backups or device-to-device transfers, so the server logins coming in later updates stay on this phone.
