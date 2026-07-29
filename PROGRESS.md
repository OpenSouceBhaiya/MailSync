# MailSync — Live Project Progress
> **Last Updated:** 2026-07-25 | **Current Version:** v2.1.0
> **Always read this before starting work on a new session.**

---

## Architecture (Current — No Backend Server)
- **Android App**: Kotlin + Jetpack Compose. `OtpNotificationListenerService` captures OTPs → AES-256 GCM encrypt locally → write to Firebase Realtime DB
- **Chrome Extension**: MV3. Background service worker polls Firebase every 2s → AES-256 decrypt → copy to clipboard via Offscreen API
- **Relay**: Firebase Realtime Database (Asia Southeast 1). Acts as a blind encrypted relay — never sees plaintext OTPs
- **No separate backend** — the original Node.js backend design was replaced by direct Firebase architecture

---

## ⚠️ IMPORTANT RULES FOR ALL AI MODELS
1. **Extension = Load directly from source.** Tell user to load Chrome extension from:
   `f:\Piyush Work\Software Making\Gmail Otp Syncer\extension\`
   via Chrome → Load unpacked → select that folder. No copying, no zipping ever.
   After any extension changes, user just clicks 🔄 refresh in chrome://extensions.
2. **APK always goes to `releases/`** as `mailsync-app-vX.X.X.apk` so user knows exactly where to find it.
3. **Never delete `releases/`** — user pulls APK from there.
4. **Always update PROGRESS.md** at the end of every session.
5. **Read PROGRESS.md first** before doing any work in a new session.

---

## Releases Location
All packaged files are in: `f:\Piyush Work\Software Making\Gmail Otp Syncer\releases\`
- `mailsync-app-v2.1.0.apk` — Latest Android debug APK
- `mailsync-extension-v2.1.0.zip` — Latest Chrome Extension

---

## Android App — COMPLETED ✅
- [x] Firebase, Google Sign-In, Room DB, DataStore
- [x] OtpNotificationListenerService (background, survives app close, handles SMS + WhatsApp + Gmail notifications)
- [x] AES-256 GCM E2E encryption (key generated on Android, shared via QR visual only)
- [x] QR Code scanner (CameraX + ML Kit barcode) with Google-Pay style success tick animation
- [x] Home Screen: live OTP feed, time-based greeting, waving hand animation, gradient name
- [x] OTP Inbox Screen with full searchable history
- [x] Accounts Screen (Google OAuth, add/remove gmail accounts)
- [x] Devices Screen (active PCs, session management, unlink with animation)
- [x] Settings Screen (Instant Sync, Clipboard toggle, Battery optimization prompt)
- [x] Onboarding Screen (premium multi-step animated)
- [x] Crash Reporter (captures crash, shows copy dialog, user can report)
- [x] Background Scan (long-press shortcut via BackgroundScanActivity)
- [x] "Manage Accounts" button in home screen bottom sheet (goes to Accounts page)

## Chrome Extension — COMPLETED ✅
- [x] Manifest V3, Offscreen API for reliable clipboard copy
- [x] QR Code (QRious, Level M, 240px, perfectly centered with flexbox wrapper)
- [x] 3-minute countdown timer (no flash, initializes from stored expiry)
- [x] AES-256 GCM decryption in background service worker
- [x] Persistent polling via `chrome.alarms` (survives popup close, MV3 compliant)
- [x] Live in-popup status polling every 2.5s (no reopen needed to see state changes)
- [x] Premium pulsing status dot inside popup (green=active, red=error)
- [x] **Real Chrome toolbar icon** animated green/red dot drawn via OffscreenCanvas + `chrome.action.setIcon()`
- [x] OTP History in popup (last 3 OTPs, local storage only, tap to copy, auto-expire 30min)
- [x] macOS-style in-page toast (top-right, slides down from top, large bold OTP code)
- [x] **No Windows system notification** — in-page toast only
- [x] Auto-clear clipboard 60s after OTP copied (security)
- [x] Accurate status messages (accounts disabled vs sync paused vs permissions)
- [x] Offline UI blur when internet drops
- [x] Unlink → popup reloads with fresh QR (does NOT close)
- [x] Terminated by phone → popup auto-updates without reopen
- [x] After QR scan → instantly shows correct state (no reopen needed)
- [x] Time-based greeting (Good Morning/Afternoon/Evening) with first name from storage

## Security — COMPLETED ✅
- [x] AES-256 GCM E2E encryption (key never touches Firebase or any server)
- [x] Key transmitted via QR code only (visual, one-time)
- [x] Auto-clear clipboard after 60s
- [x] OTP history stored locally only (chrome.storage.local, not backend)
- [x] `.gitignore` blocks: `google-services.json`, `.env`, `.keystore`, `*.jks`, `build/`
- [x] `README.md` with badges and installation guide
- [x] `SECURITY.md` with Acceptable Use Policy and GPLv3 notice
- [x] `LICENSE` (GPLv3 — prevents proprietary closed-source forks)

## QR URL Security Model
The QR URL format: `https://opensourcebhaiya.online/apps/mailsync/connect?uuid=...&name=...&browser=...&key=...`
- **UUID**: Random v4 device ID. By itself, not exploitable.
- **AES Key in URL**: This is the encryption key. If someone intercepts this URL string, they CAN decrypt your OTPs from Firebase.
- **Mitigation**: Key is only visible as QR (not plaintext). Screen access = physical access = already compromised.
- **When opensourcebhaiya.online is live**: The page must NOT log or process these URL params. It should show a "Download MailSync" landing page. The app's QR scanner reads the URL params directly.

## Remaining (Optional / Post-Launch)
- [ ] GitHub Actions CI/CD (auto-build APK on push to main)
- [ ] Chrome Web Store submission (requires developer account + $5 fee)
- [x] Google Play Store release signed APK (keystore generated and applied)
- [ ] Autofill Service (auto-fill OTP into Android login fields without copy-paste)
- [ ] Custom animated app icon
- [ ] `opensourcebhaiya.online/apps/mailsync/connect` landing page (show download links, do NOT process URL params server-side)
