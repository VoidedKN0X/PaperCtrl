# PaperCtrl

## Dependency verification

Gradle checks every library and plugin download against the sha256 checksums in `gradle/verification-metadata.xml` (checksums only, no PGP). When a dependency, plugin or the Gradle version changes, start the "Dependency verification metadata" workflow (`gh api -X POST repos/VoidedKN0X/PaperCtrl/actions/workflows/verification-metadata.yml/dispatches -f ref=main`), take the file from the job log (between the BEGIN/END markers: `base64 -d | gunzip`), check the diff (only Maven Central and Google Maven artifacts the build uses) and commit it in the same push. Never turn verification off or make it lenient to get a build through.

`settings.gradle.kts` lists only `google()` and `mavenCentral()` for plugins, not `gradlePluginPortal()`: the portal serves different files for the Kotlin plugin markers, which made verification fail whenever Maven Central had a hiccup. Don't add it back unfiltered (see the comment in that file).

## Build and signing (CI)

`.github/workflows/build.yml` has four jobs: `version` (is this a release?), `build` (Gradle, **no secrets**, `UNSIGNED_RELEASE=true` gives `app-release-unsigned.apk`; runs every check), `sign` (no checkout, no Gradle: `zipalign` + `apksigner` with the release key, then checks the signer against `EXPECTED_CERT_SHA256` and that the APK's contents didn't change) and `release` (attestation, release notes, publish).

- Never give the `build` job a secret, and never run Gradle or check out the repo in `sign`. New build checks (tests, lint, APK checks) go in `build`.
- `EXPECTED_CERT_SHA256` in `build.yml` must match the fingerprint in `SECURITY.md` and `README.md`; the build fails if it doesn't.
- No Gradle cache in any workflow (`cache-disabled: true`): a cache saved by a failed run used to break every later build.

## Releasing

- A release is published only when `versionName` in `app/build.gradle.kts` is bumped. Bump `versionCode` by 1 at the same time.
- Tags are `vX.Y.Z` and releases are named "PaperCtrl X.Y.Z". Never add a build number (`-build.N`, "(build N)").
- Use the same version for every change that ships together; README, licence and CI changes don't need a bump.

## Changelog

Every new version needs a detailed entry at the top of `CHANGELOG.md`, under a heading for that version (match the existing headings). CI refuses to release without one, and the entry becomes the release notes on GitHub, in Obtainium and in the CtrlF-Droid repo.

- Write for people using the app, not for developers: what's new, what changed, what was fixed.
- Cover every user-visible change in the version, one bullet each, with enough detail to understand it without opening the app.
- Group with `### New`, `### Changed`, `### Fixed` and `### Security` when there's more than a couple of items.

## Encryption at rest

- Sealed with AES-256-GCM (`data/crypto/SealedFile`, marker `PAPERCTRL-REST1`) under the data key (`DataKeyStore`: 32 random bytes in `filesDir/data.key`, wrapped by the Keystore key `paperctrl_data_at_rest` through `KeystoreWrapper`/`KeystoreAes`; no user authentication, so uploads work in the background; `AppContainer.dataKeys`): `upload-queue/queue.json`, each `upload-queue/<id>/payload` of at most 16 MB (`UploadQueue.MAX_SEALED_PAYLOAD`), `scan-session/*.jpg` (page photos and previews), `list-cache/*.json` and the documents kept offline (`offline/<server>/<id>.json` and `.bin`, `OfflineStore`: always sealed, no ordinary-file fallback, at most 32 MB; shown by decrypting into `cacheDir/docs/offline`; an unreadable copy is only removed by the user and is moved aside, not overwritten, when the document is kept again).
- Not sealed, on purpose: viewed documents in `cacheDir/docs` and `cacheDir/shared` (PdfRenderer needs a seekable file, the FileProvider hands real files to other apps), payloads over 16 MB (GCM needs the whole file in memory), `upload-staging/` and camera/import temp files (moments only), `scan-session/pages.json` (no page content), DataStore settings (server list; tokens are encrypted separately by `KeystoreCrypto`/`LockVault`, which this doesn't touch).
- Readers must accept both sealed and ordinary files (`SealedFile.isSealed`, `SealedFile.read`). Without a data key the app reads and writes ordinary files as before.
- Never delete or overwrite a file that can't be opened, never replace an existing `data.key`, and never create a Keystore key on the decrypt path (`KeystoreAes.existing`). An ordinary file only becomes a sealed one through `SealedFile.sealInPlace` (temp file, sync, read back, compare, atomic rename). Waiting files are only sealed in `UploadQueue.load` (before `loaded`, so before any worker reads them) and in `enqueue` (before the item exists); never while an upload could be reading them.
- The Keystore is only asked on background threads: `UploadQueue` reads its list after construction (`loaded`/`awaitLoaded`; workers and anything that needs the real list must wait for it), `ScanSession` checks its photos after construction.
- Irreplaceable (queue, scans): a sealed `queue.json` or sealed page photos that can't be opened turn `UploadQueue.unreadable` / `ScanSession.unreadable` on: nothing is written, deleted, cleaned up or scheduled, and the Scan tab shows the notice with "Try again" (`AppContainer.retryUnreadable`) and "Start empty" (`startEmptyUnreadable`: renames the folder to `<name>.unreadable-<time>`, never deletes). Cache (`list-cache`): an unopenable file is deleted and fetched again.
- Sharing, "Open with", Save to Downloads and the upload itself still produce the ordinary file formats.
- `SealedFileTest` covers the format, the migration step and the key rules (`./gradlew :app:testDebugUnitTest`; CI doesn't run it).

## Constraints

- Fully FOSS, buildable for F-Droid: no Google Play Services, Firebase or ML Kit. There is one build flavor.
- arm64-v8a only (`abiFilters` in `app/build.gradle.kts`).
- Every user-facing string lives in `res/values/strings.xml`.
- Never log document contents, tokens, or file paths with personal data.
- Paperless-ngx API: support API versions 9 and 10, negotiated from the server's `X-Api-Version` header; refuse older servers with a clear message.
