# Changelog

## Unreleased

### New
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
