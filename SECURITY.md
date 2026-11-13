# Security policy

Thank you for helping keep PaperCtrl and its users safe.

## Reporting a problem

**Please don't report security problems in a public issue.** Report them privately instead:

1. Go to the [Security tab](https://github.com/VoidedKN0X/PaperCtrl/security) of this repo.
2. Click **Report a vulnerability** and describe what you found, how to reproduce it, and which version of PaperCtrl you used.

Only the maintainer can see the report. I'll reply as soon as I can, work on a fix, and credit you in the changelog if you'd like.

## Supported versions

Only the latest release gets security fixes. Please update before reporting, and check the [changelog](CHANGELOG.md) to see whether it's already fixed.

## What counts

- Your password, API token, documents or other data leaking to anyone other than your own Paperless-ngx server
- Document files, scans or thumbnails left readable on the phone, or reachable by other apps, when they shouldn't be
- Ways around the app lock, or protected screens or document titles showing up in screenshots, the recent-apps view or notifications when they shouldn't
- Another app on the phone being able to make PaperCtrl do something you didn't ask for (for example through Share to PaperCtrl or ntfy push)
- Anything that lets someone run code or read files they shouldn't

Problems in Paperless-ngx itself should go to that project.

## How releases are protected

Every release is built from this repo by GitHub Actions and signed with the same release key. Its SHA-256 certificate fingerprint is:

```
00:D1:B2:3E:CC:53:BA:51:98:B3:26:71:4D:1D:70:E8:3E:3A:BD:75:E2:E7:20:E6:90:B0:F0:6F:24:84:CB:6E
```

The [CtrlF-Droid repo](https://voidedkn0x.github.io/CtrlF-Droid/) only accepts APKs signed with this key.
