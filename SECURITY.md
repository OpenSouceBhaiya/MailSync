# Security Policy for MailSync

Security and privacy are the foundational pillars of MailSync. We operate under a strict **Zero-Knowledge** threat model.

## 🔒 Our Security Architecture

1. **Local-First Processing:**
   All scanning, extracting, and processing of emails/OTPs happens *locally* on your Android device. Your data is never sent to a backend server for processing.

2. **End-to-End Encryption (E2EE):**
   When an OTP is beamed from your phone to your PC, it is encrypted using **AES-256-GCM**. The encryption keys are generated entirely locally when you scan the pairing QR code. The keys never leave your devices.

3. **Ephemeral Relays:**
   We use Firebase Realtime Database strictly as a blind relay. Because the payloads are E2EE encrypted, neither we nor Google can read the contents. Furthermore, payloads are set to self-destruct from the relay within 5 seconds.

## 🚨 Reporting a Vulnerability

We take all security vulnerabilities seriously. 

If you discover a security vulnerability within MailSync, please **DO NOT** open a public issue on GitHub. Instead, report it privately so we can issue a patch before malicious actors can exploit it.

Please send an email to our security team or use our secure contact form:
- **Contact Form:** [http://opensourcebhaiya.online/contact](http://opensourcebhaiya.online/contact)

We will endeavor to respond to your report within 48 hours and work with you to understand and resolve the issue swiftly.

## 🛡️ Supported Versions

We only provide security updates for the latest major version of the application. Please ensure you are running the most recent version of both the Android App and the Browser Extension.
