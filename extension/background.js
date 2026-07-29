// background.js — MailSync Chrome Extension

// ─── Keep-Alive Alarm ────────────────────────────────────────────────────────
chrome.alarms.create("keepAlive", { periodInMinutes: 1 });
chrome.alarms.onAlarm.addListener((alarm) => {
    if (alarm.name === "keepAlive") {
        chrome.storage.local.get(['linked', 'uuid', 'aesKey'], (data) => {
            if (data.linked && data.uuid && data.aesKey) {
                startListening(data.uuid, data.aesKey);
            }
        });
    }
});

// ─── Boot: Restore listening if already linked ────────────────────────────────
chrome.storage.local.get(['linked', 'uuid', 'aesKey'], (data) => {
    if (data.linked && data.uuid && data.aesKey) {
        startListening(data.uuid, data.aesKey);
    }
});

// ─── Message Handler ──────────────────────────────────────────────────────────
chrome.runtime.onMessage.addListener((message) => {
    if (message.action === "start_listening") {
        chrome.storage.local.get(['uuid', 'aesKey'], (data) => {
            if (data.uuid && data.aesKey) {
                startListening(data.uuid, data.aesKey);
            }
        });
    } else if (message.action === "stop_listening") {
        isListening = false;
        setToolbarIcon('none');
    } else if (message.action === "copy_to_clipboard") {
        copyToClipboard(message.text);
    } else if (message.action === "pc_login_detected") {
        // Write "PC is on login page" flag to Firebase so Android app can show alert
        chrome.storage.local.get(['uuid'], (data) => {
            if (!data.uuid) return;
            fetch(`https://mailsync-relay-default-rtdb.asia-southeast1.firebasedatabase.app/devices/${data.uuid}/pcLoginActive.json`, {
                method: 'PUT',
                body: JSON.stringify({ active: true, host: message.url, ts: Date.now() })
            }).catch(() => {});
        });
    } else if (message.action === "pc_login_cleared") {
        chrome.storage.local.get(['uuid'], (data) => {
            if (!data.uuid) return;
            fetch(`https://mailsync-relay-default-rtdb.asia-southeast1.firebasedatabase.app/devices/${data.uuid}/pcLoginActive.json`, {
                method: 'DELETE'
            }).catch(() => {});
        });
    } else if (message.action === "sse_otp") {
        chrome.storage.local.get(['uuid', 'aesKey'], (data) => {
            if (data.uuid && data.aesKey) fetchOtpImmediately(data.uuid, data.aesKey);
        });
    } else if (message.action === "sse_device") {
        chrome.storage.local.get(['uuid'], (data) => {
            if (data.uuid) {
                // Fetch the device JSON to immediately update UI state
                fetch(`https://mailsync-relay-default-rtdb.asia-southeast1.firebasedatabase.app/devices/${data.uuid}.json?_t=${Date.now()}`)
                    .then(r => r.json())
                    .then(statusData => {
                        if (statusData) {
                            const isError = statusData.status === 'error_no_accounts' || statusData.syncEnabled === false || statusData.status === 'paused';
                            setToolbarIcon(isError ? 'error' : 'active');
                            chrome.runtime.sendMessage({ action: "status_update", statusData }).catch(() => {});
                        }
                    }).catch(()=>{});
            }
        });
    }
});

// ─── State ───────────────────────────────────────────────────────────────────
let isListening = false;
let lastHandledOtp = null;
let iconAnimInterval = null;
let iconDotVisible = true;

// ─── Toolbar Icon with Animated Dot ──────────────────────────────────────────
async function setToolbarIcon(state) {
    try {
        // Load the base icon image
        const resp = await fetch(chrome.runtime.getURL('icon128.png'));
        const blob = await resp.blob();
        const bitmap = await createImageBitmap(blob);

        const size = 128;
        const canvas = new OffscreenCanvas(size, size);
        const ctx = canvas.getContext('2d', { willReadFrequently: true });

        // Clear previous animation interval
        if (iconAnimInterval) {
            clearInterval(iconAnimInterval);
            iconAnimInterval = null;
        }

        async function drawFrame(dotVisible) {
            ctx.clearRect(0, 0, size, size);
            ctx.drawImage(bitmap, 0, 0, size, size);

            if (state === 'active') {
                const dotR = 18;
                const cx = size - dotR - 4;
                const cy = dotR + 4;

                // Outer glow ring (animated pulse)
                if (dotVisible) {
                    ctx.beginPath();
                    ctx.arc(cx, cy, dotR + 6, 0, 2 * Math.PI);
                    ctx.fillStyle = 'rgba(16, 185, 129, 0.3)';
                    ctx.fill();
                }

                // Solid dot
                ctx.beginPath();
                ctx.arc(cx, cy, dotR, 0, 2 * Math.PI);
                ctx.fillStyle = '#10b981';
                ctx.fill();

                // Border
                ctx.beginPath();
                ctx.arc(cx, cy, dotR, 0, 2 * Math.PI);
                ctx.strokeStyle = '#ffffff';
                ctx.lineWidth = 3;
                ctx.stroke();

            } else if (state === 'error') {
                const dotR = 18;
                const cx = size - dotR - 4;
                const cy = dotR + 4;

                if (dotVisible) {
                    ctx.beginPath();
                    ctx.arc(cx, cy, dotR + 6, 0, 2 * Math.PI);
                    ctx.fillStyle = 'rgba(239, 68, 68, 0.3)';
                    ctx.fill();
                }

                ctx.beginPath();
                ctx.arc(cx, cy, dotR, 0, 2 * Math.PI);
                ctx.fillStyle = '#ef4444';
                ctx.fill();

                ctx.beginPath();
                ctx.arc(cx, cy, dotR, 0, 2 * Math.PI);
                ctx.strokeStyle = '#ffffff';
                ctx.lineWidth = 3;
                ctx.stroke();
            }

            const imageData = ctx.getImageData(0, 0, size, size);
            await chrome.action.setIcon({ imageData: { 128: imageData } }).catch(() => {});
        }

        // Initial draw
        await drawFrame(true);

        // Animate the pulsing ring every 800ms
        if (state === 'active' || state === 'error') {
            let toggle = false;
            iconAnimInterval = setInterval(async () => {
                toggle = !toggle;
                await drawFrame(toggle);
            }, 800);
        }

    } catch (e) {
        // Fallback: just clear badge
        chrome.action.setBadgeText({ text: '' });
    }
}

// ─── Main Listener Loop ───────────────────────────────────────────────────────
async function startListening(uuid, aesKeyBase64) {
    if (isListening) return;
    isListening = true;

    const dbBaseUrl = `https://mailsync-relay-default-rtdb.asia-southeast1.firebasedatabase.app`;

    const rawKey = Uint8Array.from(atob(aesKeyBase64), c => c.charCodeAt(0));
    const cryptoKey = await crypto.subtle.importKey(
        'raw', rawKey,
        { name: 'AES-GCM', length: 256 },
        false, ['decrypt']
    );

    setToolbarIcon('active');

    // Establish zero-latency SSE via offscreen document
    try {
        const hasDoc = await chrome.offscreen.hasDocument();
        if (!hasDoc) {
            await chrome.offscreen.createDocument({
                url: 'offscreen.html',
                reasons: ['CLIPBOARD'],
                justification: 'SSE Connection and Clipboard'
            });
            await new Promise(resolve => setTimeout(resolve, 200));
        }
        chrome.runtime.sendMessage({ target: 'offscreen', type: 'start-sse', uuid }).catch(() => {});
    } catch (e) {
        reportGlobalError("Failed to start SSE: " + e.message);
    }

    const pollLoop = async () => {
        if (!isListening) {
            setToolbarIcon('none');
            return;
        }

        try {
            // Heartbeat check for device link status
            const statusRes = await fetch(`${dbBaseUrl}/devices/${uuid}.json?_t=${Date.now()}`);
            const statusData = await statusRes.json();

            if (!statusData || !statusData.dateLinked || statusData.status === 'terminated') {
                isListening = false;
                setToolbarIcon('error');
                chrome.runtime.sendMessage({ action: "device_terminated" }).catch(() => {});
                return;
            }

            const isError = statusData.status === 'error_no_accounts' || statusData.syncEnabled === false || statusData.status === 'paused';
            setToolbarIcon(isError ? 'error' : 'active');
            
            if (statusData.accountName !== undefined) {
                chrome.storage.local.set({ userName: statusData.accountName });
            } else {
                chrome.storage.local.set({ userName: null });
            }
            chrome.runtime.sendMessage({ action: "status_update", statusData }).catch(() => {});

            // Fetch OTP just in case SSE missed it
            await fetchOtpImmediately(uuid, aesKeyBase64, cryptoKey);
        } catch (e) {
            // Silent — network hiccup, retry next loop
        }

        if (isListening) {
            // Slow fallback polling (30s) since SSE handles real-time delivery
            setTimeout(pollLoop, 30000);
        }
    };

    pollLoop();
}

async function fetchOtpImmediately(uuid, aesKeyBase64, providedCryptoKey = null) {
    if (!isListening) return;
    try {
        const rawKey = Uint8Array.from(atob(aesKeyBase64), c => c.charCodeAt(0));
        const cryptoKey = providedCryptoKey || await crypto.subtle.importKey(
            'raw', rawKey,
            { name: 'AES-GCM', length: 256 },
            false, ['decrypt']
        );
        
        const dbBaseUrl = `https://mailsync-relay-default-rtdb.asia-southeast1.firebasedatabase.app`;
        const otpsUrl = `${dbBaseUrl}/otps/${uuid}.json`;
        const otpRes = await fetch(`${otpsUrl}?_t=${Date.now()}`);
        const otpData = await otpRes.json();

        if (otpData && otpData.iv && otpData.data) {
            const sig = otpData.iv + otpData.data;
            if (sig !== lastHandledOtp) {
                lastHandledOtp = sig;
                await fetch(otpsUrl, { method: 'DELETE' }).catch(()=>{});
                await handleEncryptedOtp(otpData.iv, otpData.data, cryptoKey);
            }
        }
    } catch(e) {
        // silent
    }
}

// ─── Clipboard ───────────────────────────────────────────────────────────────
async function copyToClipboard(text) {
    try {
        const hasDoc = await chrome.offscreen.hasDocument();
        if (!hasDoc) {
            await chrome.offscreen.createDocument({
                url: 'offscreen.html',
                reasons: ['CLIPBOARD'],
                justification: 'Copy OTP to clipboard'
            });
            // Give offscreen doc a tiny moment to initialize its listener
            await new Promise(resolve => setTimeout(resolve, 100));
        }
        chrome.runtime.sendMessage({ type: 'copy-data-to-clipboard', target: 'offscreen', data: text });
    } catch (e) { /* offscreen unavailable */ }
}

function reportGlobalError(msg) {
    chrome.storage.local.get(['globalErrors'], (data) => {
        const errors = data.globalErrors || [];
        errors.unshift({ msg, time: Date.now() });
        if (errors.length > 5) errors.pop();
        chrome.storage.local.set({ globalErrors: errors });
        chrome.runtime.sendMessage({ action: "global_error_updated" }).catch(() => {});
    });
}

// ─── Decrypt & Deliver OTP ───────────────────────────────────────────────────
async function handleEncryptedOtp(ivBase64, dataBase64, cryptoKey) {
    try {
        const iv = Uint8Array.from(atob(ivBase64), c => c.charCodeAt(0));
        const cipher = Uint8Array.from(atob(dataBase64), c => c.charCodeAt(0));

        const decrypted = await crypto.subtle.decrypt({ name: 'AES-GCM', iv }, cryptoKey, cipher);
        const text = new TextDecoder().decode(decrypted);

        // Format: "otpCode|sender|timestamp"
        const parts = text.split('|');
        if (parts.length < 2) {
            reportGlobalError("Decrypted OTP payload format invalid.");
            return;
        }

        const otpCode = parts[0].trim();
        const sender = parts[1].trim();

        // 1. Copy to clipboard
        await copyToClipboard(otpCode);

        // 2. Auto-clear clipboard after 60s
        setTimeout(() => copyToClipboard(' '), 60000);

        // 3. Save to local history (last 3, no backend)
        chrome.storage.local.get(['otpHistory'], (data) => {
            const history = data.otpHistory || [];
            history.unshift({ otp: otpCode, sender, time: Date.now() });
            if (history.length > 3) history.pop();
            chrome.storage.local.set({ otpHistory: history });
            chrome.runtime.sendMessage({ action: "otp_received", otpCode, sender }).catch(() => {});
        });

        // 4. In-page toast — NO Windows notification
        chrome.tabs.query({ active: true, currentWindow: true }, (tabs) => {
            if (!tabs || !tabs.length) {
                reportGlobalError("No active tab found to show OTP toast.");
                return;
            }
            let successCount = 0;
            let lastError = null;

            tabs.forEach(tab => {
                chrome.tabs.sendMessage(tab.id, {
                    action: "show_toast_and_copy",
                    otp: otpCode,
                    sender
                }).then(() => {
                    successCount++;
                }).catch(async (err) => {
                    if (err.message.includes("Receiving end does not exist") || err.message.includes("Could not establish connection")) {
                        try {
                            // The tab doesn't have our content script! Let's force inject it.
                            await chrome.scripting.insertCSS({ target: { tabId: tab.id }, files: ["content.css"] });
                            await chrome.scripting.executeScript({ target: { tabId: tab.id }, files: ["content.js"] });
                            
                            // Try sending again after injection
                            await chrome.tabs.sendMessage(tab.id, { action: "show_toast_and_copy", otp: otpCode, sender });
                            successCount++;
                        } catch (injectErr) {
                            lastError = injectErr.message;
                        }
                    } else {
                        lastError = err.message;
                    }
                });
            });

            // If we couldn't show it anywhere, report the error after a brief delay
            // BUT ignore restricted page errors, as they are expected when user is on a new tab or settings page
            setTimeout(() => {
                if (successCount === 0 && lastError) {
                    const ignoredErrors = ["Cannot access contents of the page", "Receiving end does not exist", "Could not establish connection"];
                    const shouldIgnore = ignoredErrors.some(ignored => lastError.includes(ignored));
                    if (!shouldIgnore) {
                        reportGlobalError(`Warning: Could not show toast on page (${lastError})`);
                    }
                }
            }, 1000);
        });

    } catch (e) {
        reportGlobalError(`Decryption or handling failed: ${e.message}`);
    }
}
