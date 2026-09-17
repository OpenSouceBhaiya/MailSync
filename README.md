<div align="center">
  <img src="https://opensourcebhaiya.online/logo.png" alt="MailSync Logo" width="120" />
  <h1>MailSync</h1>
  <p><strong>A Local-First, Zero-Knowledge OTP Synchronization Utility</strong></p>

  [![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
  [![Platform: Android](https://img.shields.io/badge/Platform-Android-green.svg)]()
  [![Browser: Chrome](https://img.shields.io/badge/Browser-Chrome/Edge-yellow.svg)]()
  [![Website](https://img.shields.io/badge/Website-OpenSourceBhaiya-orange)](http://opensourcebhaiya.online/)

</div>

<br />

MailSync is a privacy-first, open-source tool designed to securely beam incoming One-Time Passwords (OTPs) from your Android device directly to your PC's clipboard. It is built to seamlessly bridge the gap between your mobile device and desktop workflow without compromising your privacy.

Unlike traditional cloud-sync applications, MailSync operates on a strict **Local-First, Zero-Knowledge** architecture. Your OTPs and sensitive data are processed locally and are never stored on any server.

---

## 🚀 Key Features

* **⚡ Lightning Fast Sync:** Beams OTPs from your phone to your PC clipboard in under 500ms.
* **🔒 Military-Grade Encryption:** Utilizes AES-256-GCM End-to-End Encryption. Only your paired PC has the keys required to decrypt the payload.
* **🕵️ Zero-Knowledge Architecture:** Firebase is utilized strictly as a transient, real-time relay. Encrypted packets self-destruct within 5 seconds and are completely unreadable by our systems or any third-party.
* **🎨 Seamless UI/UX:** Enjoy Mac-style slide-in notifications on your PC whenever an OTP is securely copied, keeping you in the flow.
* **🔋 Battery Optimized:** Leverages the native Android Notification Listener to instantly detect OTPs without polling, saving your battery life.

---

## 📥 Installation & Setup

### 1. Android Application
1. Download the latest `MailSync-App.apk` from the [Releases page](../../releases).
2. Install the application on your Android device.
3. Grant **Notification Access** when prompted to allow the app to detect incoming OTPs.

### 2. Browser Extension (PC)
1. Download `MailSync-Extension.zip` from the [Releases page](../../releases) and extract the folder.
2. Open your chromium-based browser (Chrome, Edge, Brave, etc.) and navigate to `chrome://extensions`.
3. Enable **Developer Mode** (usually a toggle in the top right).
4. Click **Load unpacked** and select the folder you just extracted.

### 3. Pairing Your Devices
1. Click the MailSync extension icon in your browser to reveal your unique QR code.
2. Open the MailSync app on your Android device, navigate to the **Devices** tab, and scan the QR code.
3. You are now securely paired via End-to-End Encryption!

---

## 🛡️ Privacy & Security

We believe your data is yours alone. For a detailed breakdown of our security practices, encryption methods, and threat models, please refer to our [SECURITY.md](SECURITY.md).

---

## 🤝 Contributing

We welcome contributions from the open-source community! Whether it's a bug fix, a new feature, or a documentation improvement, your help is appreciated. Please read our [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines on how to get started.

---

## 📝 License

This project is licensed under the **GNU General Public License v3.0 (GPLv3)**. 

You are completely free to use, modify, and distribute this software. However, any derivative works, modifications, or applications based on this codebase **must also be open-source under the same GPLv3 license**. This legally ensures MailSync remains open and cannot be taken, closed-source, or monetized by proprietary corporations.

See the [LICENSE](LICENSE) file for the full legal text.

---

<div align="center">
  <p>Maintained with ❤️ by <strong>OpenSourceBhaiya</strong></p>
  <a href="http://opensourcebhaiya.online/">Visit our Website</a> | <a href="http://opensourcebhaiya.online/contact">Contact Us</a> | <a href="http://opensourcebhaiya.online/bug-report">Report a Bug</a>
</div>
