# Changelog

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
