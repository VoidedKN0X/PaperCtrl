# Changelog

## 0.6.0

### New

- **Light mode that follows the device theme.** PaperCtrl now switches between dark and light by itself, the moment you change your phone's dark theme setting (or when it switches on a schedule). There's still no theme setting in the app and no wallpaper colours: it simply follows the phone.
- **"Signal Light" colours:** a warm off-white background (#F5F5F1), white cards with a soft grey border, near-black text and grey secondary text. Hero cards, main buttons, the open tab, switches that are on and the PIN dots keep the same lavender accent fill with dark text as in dark mode. Accent-coloured text and icons (the slash in PAPER/CTRL, note counts, links, spinners, the fingerprint key, a field you're typing in) use a deeper purple (#6A3BDB) in light mode so they stay readable on white.

### Changed

- **Status and navigation bars follow the theme too:** they show the app background, with light icons in dark mode and dark icons in light mode. The window colour while PaperCtrl starts matches the theme as well, so there's no dark flash in light mode.
- **Every screen works in both themes:** inbox, documents (list and grid), document details, text, notes and history, filters, organise lists and editors, the upload queue, scan review and page editing, sharing to PaperCtrl, signing in, your profile, settings, ntfy, all sheets and dialogs, and the lock and PIN screens (white keys with a thin border in light mode).
- **Switches in light mode:** a light grey track with a grey knob when off; the accent track with a dark knob when on.
- **Progress bars in light mode:** a light grey track with a near-black fill (accent for the main progress).
- **Errors in light mode** use a deeper red (#D93636) that reads well on white.
- **Home screen widget:** now uses the Ctrl colours and follows the phone's dark or light setting, with an accent Scan button. It no longer takes its colours from the wallpaper.
- **Notifications** show the PaperCtrl accent colour on their icon, matching the theme.

### Not changed

- **The camera scanner stays dark in both themes**, so its buttons and hints stay clear over the live camera picture. The ASN barcode scanner stays dark for the same reason.
- **Document pages are still shown on white**, like paper, in both themes.

## 0.5.0

PaperCtrl has a new look: "Ctrl / Signal", the design every Ctrl app now shares. Everything works as before: the same screens, tabs, settings and features, only restyled.

### Changed

- **Dark only, with one purple accent.** The whole app uses a near-black background (#08080A), dark grey cards with a thin border, off-white text and a single lavender accent (#B48CFF) for whatever matters most. The status and navigation bars use the same background, edge to edge, with light icons.
- **New fonts.** All text is set in Space Grotesk; numbers, dates, document numbers, section labels and the wordmark use JetBrains Mono. Both fonts are bundled in the app (no font downloads, nothing from Google) and replace Manrope.
- **New top bar.** The main tabs show a **PAPER/CTRL** wordmark on the left (the slash in the accent colour) and your profile picture on the right: a rounded square with your initials and a small accent square while you're connected. Tapping it still opens your profile and settings. Other screens have a square back button and their title.
- **Floating bottom bar.** The tab bar is now a dark rounded bar floating above the bottom of the screen. The open tab is an accent block showing its icon and name; the other tabs show only their icon. Lists scroll clear of it. On tablets the side rail uses the same accent.
- **Inbox:** the number of documents to sort now sits in a large accent card at the top, with the swipe hint below it. Swiping a document right shows an accent background, swiping left a grey one.
- **Document lists:** plain rows separated by thin lines, each starting with the document number, date and ASN in mono, then the title and the correspondent and type. Note counts are in the accent colour. Grid view uses bordered dark cards.
- **Tags are small square chips**: 9dp rounded corners, the tag's own colour as a small square dot next to its name, instead of fully coloured round pills.
- **Scanner:** the shutter is now an accent rounded square with a camera icon, inside a ring that fills in the accent colour while auto-capture waits for a steady page. Flashlight, auto-capture and gallery buttons are dark rounded squares (accent when switched on), the hint is a dark mono label, and the page counter is an accent chip.
- **Scan tab:** a large accent card shows how many scanned pages are waiting (tap it to continue scanning); **Upload files** is a dark outlined button.
- **Switches:** every switch is a rounded-rectangle Ctrl switch: accent track with a dark knob when on, grey when off. TalkBack still reads them as switches.
- **Buttons, fields and progress:** main buttons are accent-filled with dark text and 18dp corners; secondary buttons are dark with a border. Text fields are dark with a border that turns accent when you type, and an accent cursor. Upload and download progress uses a slim bar.
- **Lock screen:** an accent lock badge, "VKNXWORKS · CTRL", a big **Locked** title, square PIN cells that fill in the accent colour, and a 3-column keypad of large rounded keys with mono digits. The fingerprint key (outlined in the accent) sits bottom-left when fingerprint unlock is on, and turns into **OK** once you've typed enough digits for a PIN of a different length; delete sits bottom-right. **Forgot PIN?** is below the keypad. How the lock works hasn't changed. The PIN set-up and change screens use the same keypad.
- **Profile (You) page:** an accent avatar with your initials, your name and the server address in mono, then cards for **Server**, **App** (Settings) and **About**. **Support PaperCtrl** is now an accent button at the bottom, above a "GOOGLE-FREE · TELEMETRY-FREE · ARM64" footer and the version line.
- **Settings and every grouped list** use dark cards with mono section labels (PRIVACY AND SECURITY, NOTIFICATIONS…) and thin lines between rows. The **Allow** button on the "Notifications are blocked" row is accent-filled.
- **Sheets and dialogs** (filters, details, choices, share links, confirmations, certificate check) are dark cards with 26dp corners and a thin border.
- **Organise lists** show each item's number in mono and its colour as a small square.
- **Signing in** shows the PAPER/CTRL wordmark and the new fields and buttons.
- Icons use the outlined style throughout.

### Removed

- **Theme** (Follow system / Dark / Light / Pure black) and **Use wallpaper colours** are gone from **Settings**, along with the whole Appearance section: PaperCtrl is always dark now. Your old choice is kept on the phone but no longer does anything.
- The frosted-glass blur behind the tab bar is gone, and with it the library that drew it, so the app is slightly smaller.

## 0.4.8

### Fixed

- **PaperCtrl now asks for permission to show notifications.** On Android 13 and later an app has to be allowed to show notifications, but PaperCtrl only asked when you shared a file to it. Scans uploaded from the app itself, the "Document added" and "Upload failed" notifications (both on from the start), the inbox reminder and ntfy pushes were all silently dropped on a fresh install, including the progress notification while an upload runs. PaperCtrl now asks once, by itself, as soon as you're signed in and the main screens show, and never nags again after that.
- **The notification switches tell the truth.** While notifications are blocked, **Document added**, **Upload failed**, the **Daily inbox reminder** and **Notifications from Paperless** (ntfy) no longer show "on". A **Notifications are blocked** row at the top of **Settings > Notifications** (and on the **Push from Paperless (ntfy)** screen) says so, with an **Allow** button: it shows Android's permission dialog while Android still offers it, and otherwise opens PaperCtrl's notification settings. Coming back from there updates the switches straight away.
- **Turning on a notification switch asks for the permission** when notifications are blocked, instead of switching on something that can't reach you.
- Showing Android's notification dialog or settings no longer locks the app when you come back to it.

## 0.4.7

### Fixed

- **Fingerprint unlock works again after you leave the app.** Once you'd left PaperCtrl with the back gesture or by swiping home, the lock screen no longer asked for your fingerprint when you came back, and tapping **Use fingerprint** did nothing either; only the PIN worked until the app was force-closed. The fingerprint prompt was asked for while the app was still coming back on screen, and the phone dropped it without answering, which left PaperCtrl waiting for an answer that never came. Now the prompt only opens once PaperCtrl is fully back on screen, a prompt that never answered can't block the next one, and the lock screen offers your fingerprint again every time you come back to it, not just the first time after it locked. The same applies to the one-time check with your phone's screen lock for people updating from the old app lock.

## 0.4.6

### Security

- **Viewed documents are deleted when PaperCtrl locks**: with the app lock on, the documents you'd opened (and the copies made for sharing or **Open with**) stayed in PaperCtrl's private storage on the phone while it was locked, until the app restarted. They're now deleted the moment PaperCtrl locks, and a document that was open is downloaded again after you unlock.
- **Your server tokens are always sealed with the app lock**: if PaperCtrl was stopped in the split second between setting up the app lock and encrypting your server tokens with the PIN, they could stay encrypted with the Android Keystore only. PaperCtrl now checks every time you unlock and seals anything that was missed.

## 0.4.5

### Changed

- **All text is now in one place, ready for translations**: the last four messages that were still written into the code now come from the app's text file too. They read the same as before: "This address leads to a different Paperless server.", "The server didn't return a task for the upload." and, for a failed upload, "It is a duplicate of document #…".
- **Clearer message when an address keeps redirecting**: instead of "Something answered at this address, but not Paperless-ngx's API" followed by "Too many redirects", PaperCtrl now says the address keeps redirecting, so Paperless-ngx couldn't be reached, and to check the address.
- **README**: the App lock and privacy setting paths now start at your profile picture (tap your profile picture › Settings › Privacy and security)

## 0.4.4

### Changed

- **Hide in screenshots** and **Hide in recent apps** are now two separate switches in **Settings > Privacy and security**, both off by default. **Hide in screenshots** makes screenshots and screen recordings show a blank screen (the recent-apps view too). **Hide in recent apps** only shows a blank card in the recent-apps view, so screenshots still work. They replace the single "Hide in screenshots and recent apps" (formerly "Block screenshots") switch: if you had turned that on, both new switches are on after the update, so nothing changes for you.
- **The recent-apps preview is no longer always blank.** Before, PaperCtrl always showed a blank card in recent apps; now it only does with **Hide in recent apps** on, or while the app lock is on (a preview would otherwise show your documents without the PIN). Turn on **Hide in recent apps** to keep the old behaviour. Sharing to PaperCtrl follows the same switches.
- Signing in and the lock and PIN screens are still always kept out of screenshots and recordings, whatever the switches say.

## 0.4.3

Settings now follow the same layout as the other Ctrl apps, and signing in and the lock screen are better protected.

### Changed

- **Settings are in the same order as in every Ctrl app:** Appearance first, then Notifications, then **Privacy and security** (renamed from Privacy: app lock, change app PIN, fingerprint unlock, lock again and hide in screenshots), then Scanning. Theme, wallpaper colours and the screenshot setting have icons like the other rows.
- **"Block screenshots" is now called "Hide in screenshots and recent apps"**, the same name as in the other Ctrl apps. It works as before: screenshots and screen recordings show a blank screen.
- **Clearer note about background work with the app lock on:** an upload that's already sending still finishes, while uploads that haven't started yet, the daily inbox reminder and the widget's inbox count wait until you unlock. Push from Paperless through ntfy keeps working.
- **Slightly smaller app:** two libraries that were no longer used are gone.

### Security

- **Signing in is always kept out of screenshots and screen recordings**, even with "Hide in screenshots and recent apps" off, because your password, API token or two-factor code is typed (and can be shown) there.
- **The lock screen and every PIN screen are kept out of screenshots and screen recordings**, in the app and when sharing to PaperCtrl, so a recording can't show the PIN being typed.
- **Copied share links and your push address are marked as sensitive:** Android's clipboard preview and keyboards with a clipboard history don't show or keep them.

## 0.4.2

### Security

- **Trips out of the app no longer skip the lock if you stay away.** Picking files, sharing, opening a document in another app and links to the browser still don't count as leaving the app, but if you don't come back within 5 minutes (or your "Lock again" time, if that's longer), PaperCtrl locks anyway. Before, such a trip never locked, however long you were gone.

## 0.4.1

The app lock now works the same way as in every other Ctrl app, with the strongest protection of the options compared.

### Changed

- **App lock now has its own PIN**, separate from your phone's screen lock. It needs at least 6 digits, and very obvious ones (like 111111 or 123456) are refused. Someone who knows or has seen your phone's PIN can't open PaperCtrl with it
- **Updating with the app lock already on**: the first time you open PaperCtrl, unlock once more with your fingerprint, face or screen lock as before, then choose your app PIN. This step can't be skipped. (If your phone has no screen lock any more, PaperCtrl goes straight to choosing the PIN)
- **Fingerprint unlock is optional** (**Settings > Privacy > Unlock with fingerprint**). It only accepts strong biometrics such as the fingerprint sensor, not face unlock that only uses the camera, which a photo can sometimes fool. The PIN is still needed after the phone restarts and at least every 72 hours, the same as Android does for your screen lock
- **Choose when the lock asks again** (**Settings > Privacy > Lock again**): as soon as you leave the app (the new default, instead of after 1 minute), after 1, 5 or 15 minutes, after 1 hour, or only when the app restarts
- **Picking files or pictures, sharing, opening a document in another app or the browser, and the camera settings no longer count as leaving the app**, so coming back from them doesn't ask for the PIN again. Going to the home screen or another app yourself still does
- **Change app PIN** is in Settings and asks for your current PIN first. Turning the app lock off also asks for the PIN
- **Sharing files to PaperCtrl now asks for the PIN too** when the app is locked, before anything can be queued
- **With the app lock on, uploads you started still finish in the background**: the key that decrypts the login is only kept until they're done. If PaperCtrl is closed before that, they wait ("Waiting for you to unlock PaperCtrl") and continue as soon as you unlock
- **With the app lock on, the daily inbox reminder and the widget's inbox count only update while PaperCtrl is unlocked**, because the login stays encrypted while it's locked. Settings says so under the app lock

### Security

- **Your server logins are encrypted with your PIN while PaperCtrl is locked.** The key that decrypts them only exists in memory while the app is unlocked (or while your uploads finish), and is wiped when it locks. A locked app, or a copy of its files, can't use your API tokens
- **The PIN can't be guessed off the phone.** It's never stored: it's stretched with 600,000 rounds of PBKDF2 and mixed with a secret held in the phone's secure hardware (StrongBox where available), so copying PaperCtrl's files to a computer isn't enough to try PINs there
- **Wrong PINs lock the screen**: after 5 tries you wait 30 seconds, doubling with every further 5 wrong tries up to 15 minutes. Restarting the app or the phone, or changing the clock, doesn't reset the wait
- **Fingerprint unlock can't be faked**: the fingerprint has to unlock a key in the phone's secure hardware, so a tampered "success" on a rooted phone doesn't open the app. Adding a new fingerprint turns fingerprint unlock off until you turn it on again with your PIN
- **The lock can no longer be skipped** on a phone whose screen lock was removed: before, the old lock let you in when the phone had no fingerprint, face or screen lock set up
- **Forgot your PIN?** The lock screen offers to remove every server from the phone, with its queued uploads and cached data. Because the PIN encrypts your logins, there's no way around it. Everything on your Paperless server stays

## 0.4.0

### Changed

- **Push from Paperless through ntfy only.** Push notifications now always come through the [ntfy](https://f-droid.org/packages/io.heckel.ntfy/) app; other push apps can no longer be picked, and the "Push app" choice is gone. When you turn on **Push from Paperless (ntfy)**, PaperCtrl gets its push address from ntfy by itself. If you used another push app, PaperCtrl cancels its registration there when you update and switches to ntfy if it's installed. Your push address then changes, so copy the new one from the push screen into the webhook URL of your Paperless workflow. Until ntfy is installed, push is off; PaperCtrl's own notifications (finished and failed uploads, the daily inbox reminder) keep working as before
- **"Install ntfy" button**: when ntfy isn't on your phone, the push screen says so and offers an Install ntfy button that opens ntfy on F-Droid. The screen checks again when you come back to it, and PaperCtrl then gets its push address from ntfy straight away
- **"ntfy temporarily unavailable"** is shown when ntfy reports that your ntfy server can't be reached for now. Your push address stays the same and notifications arrive again once the server is back
- If ntfy can't register PaperCtrl, a **Try again** row appears under the reason
- **ASN label reading** now uses zxing-cpp, an independent open-source barcode reader, instead of the previous reader library. It reads the same barcodes and QR codes as before, still entirely on your phone, and recognises labels held sideways or upside down

### Security

- PaperCtrl only takes push messages from the ntfy app: on Android 14 and newer, a push that Android reports as sent by any other app is ignored, on top of the secret token every push must carry

### Privacy

- **Google-free and de-Googled**: nothing from Google is left in PaperCtrl. The barcode reader library published by Google is replaced (see above), and the last mentions of Google are removed from the app's texts and setup notes, such as "without Google services" on the push screen. PaperCtrl still has no Google Play services, Firebase or other Google services, never contacts Google, and works the same on de-Googled phones (GrapheneOS, CalyxOS, /e/OS, LineageOS without Google apps)
- **README**: a "Google-free and de-Googled" statement, and the push setup steps now describe ntfy as the only push app

## 0.3.3

### Privacy

- **No more usage stats: PaperCtrl is now telemetry-free.** The optional anonymous usage stats are gone completely, along with the Usage stats setting and the one-time question. The app no longer contacts VKNXWorks' stats server at all, and the settings it used are deleted from your phone when you update. PaperCtrl now only talks to your own Paperless-ngx server, and to your ntfy server if you use push notifications.

### Changed

- **README**: a Screenshots section (screenshots are coming soon), and a link preview image (`.github/assets/social-preview.png`) with PaperCtrl's icon, name and summary, for when the repo is shared on Reddit, Discord or elsewhere
- **README**: a Privacy section and a Telemetry-free badge replace the Usage stats section, and the link preview image has a Telemetry-free tag

## 0.3.2

### Changed
- **One About section in every Ctrl app**: Support, Report a bug, Source code and Licence (GPL-3.0), with the version underneath, at the bottom of the **You** page (tap your profile picture). Every Ctrl app now has the same four rows, in the same order, with the same wording and icons.
- **Report a bug** now sits right below **Support PaperCtrl** and explains what it's for: "Found a problem or have an idea? Open an issue on GitHub". It opens PaperCtrl's GitHub issues page.
- **Source code** now shows the repository address (github.com/VoidedKN0X/PaperCtrl) instead of "GitHub · GPL-3.0", and opens PaperCtrl's GitHub repository.
- **Licence** is a new row that opens the GNU General Public License v3.0, the licence PaperCtrl is released under.
- The heart on **Support PaperCtrl** is no longer red, matching the other rows and the other Ctrl apps.

## 0.3.1

### Changed
- **Support PaperCtrl** under **About** on the **You** page (tap your profile picture) opens VKNXWorks' GitHub Sponsors page. The last group on the **You** page is now headed **About**, and the support row reads "PaperCtrl is free and open source. Sponsor development on GitHub", the same as in the other Ctrl apps.

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
