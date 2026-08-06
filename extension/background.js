// background.js — MailSync Chrome Extension

// ─── Keep-Alive Alarm ────────────────────────────────────────────────────────
chrome.alarms.create("keepAlive", { periodInMinutes: 1 });
chrome.alarms.onAlarm.addListener(async (alarm) => {
    if (alarm.name !== "keepAlive") return;
    try {
        const data = await chrome.storage.local.get(['linked', 'uuid', 'aesKey']);
        if (!data.linked || !data.uuid || !data.aesKey) return;
        const hasDoc = await chrome.offscreen.hasDocument().catch(() => false);
        if (!hasDoc) {
            await startListening(data.uuid, data.aesKey, true);
        } else {
            chrome.runtime.sendMessage({ target: 'offscreen', type: 'ping-sse' }, (response) => {
                if (chrome.runtime.lastError || !response || !response.alive) {
                    startListening(data.uuid, data.aesKey, true);
                } else {
                    fetchOtpImmediately(data.uuid, data.aesKey);
                }
            });
        }
    } catch(e) { /* silent */ }
});

// ─── Boot: Restore listening if already linked ────────────────────────────────
chrome.runtime.onStartup.addListener(() => {
    chrome.storage.local.get(['linked', 'uuid', 'aesKey'], async (data) => {
        if (data.linked && data.uuid && data.aesKey) {
            const hasDoc = await chrome.offscreen.hasDocument().catch(() => false);
            startListening(data.uuid, data.aesKey, !hasDoc);
        }
    });
});

chrome.storage.local.get(['linked', 'uuid', 'aesKey'], async (data) => {
    if (data.linked && data.uuid && data.aesKey) {
        // Ping firebase to tell Android this is a v2 extension that supports pcLoginActive
        fetch(`https://mailsync-osb-default-rtdb.asia-southeast1.firebasedatabase.app/devices/${data.uuid}/extensionVersion.json`, {
            method: 'PUT',
            body: '2'
        }).catch(() => {});

        const hasDoc = await chrome.offscreen.hasDocument().catch(() => false);
        startListening(data.uuid, data.aesKey, !hasDoc);
    }
});

// ─── Message Handler ──────────────────────────────────────────────────────────
chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
    if (message.action === "start_listening") {
        chrome.storage.local.get(['uuid', 'aesKey'], (data) => {
            if (data.uuid && data.aesKey) {
                startListening(data.uuid, data.aesKey, true);
            }
        });
    } else if (message.action === "stop_listening") {
        isListening = false;
        setToolbarIcon('none');
        if (iconAnimInterval) { clearInterval(iconAnimInterval); iconAnimInterval = null; }
        // Close offscreen doc when explicitly stopped
        chrome.offscreen.hasDocument().then(has => {
            if (has) chrome.offscreen.closeDocument().catch(() => {});
        }).catch(() => {});
    } else if (message.action === "copy_to_clipboard") {
        copyToClipboard(message.text);
    } else if (message.action === "pc_login_detected") {
        chrome.storage.local.get(['uuid'], (data) => {
            if (!data.uuid) return;
            fetch(`https://mailsync-osb-default-rtdb.asia-southeast1.firebasedatabase.app/devices/${data.uuid}/pcLoginActive.json`, {
                method: 'PUT',
                body: JSON.stringify({ active: true, host: message.url, ts: Date.now() })
            }).catch(() => {});
        });
    } else if (message.action === "pc_login_cleared") {
        chrome.storage.local.get(['uuid'], (data) => {
            if (!data.uuid) return;
            fetch(`https://mailsync-osb-default-rtdb.asia-southeast1.firebasedatabase.app/devices/${data.uuid}/pcLoginActive.json`, {
                method: 'DELETE'
            }).catch(() => {});
        });
    } else if (message.action === "sse_otp") {
        // SSE fired a real OTP event — fetch it immediately
        chrome.storage.local.get(['uuid', 'aesKey'], (data) => {
            if (data.uuid && data.aesKey) fetchOtpImmediately(data.uuid, data.aesKey);
        });
    } else if (message.action === "sse_device") {
        chrome.storage.local.get(['uuid'], (data) => {
            if (data.uuid) {
                fetch(`https://mailsync-osb-default-rtdb.asia-southeast1.firebasedatabase.app/devices/${data.uuid}.json?_t=${Date.now()}`)
                    .then(r => r.json())
                    .then(statusData => {
                        if (statusData) {
                            const isError = statusData.status === 'error_no_accounts' || statusData.syncEnabled === false || statusData.status === 'paused';
                            setToolbarIcon(isError ? 'error' : 'active');
                            chrome.runtime.sendMessage({ action: "status_update", statusData }).catch(() => {});
                        }
                    }).catch(() => {});
            }
        });
    } else if (message.action === "capture_tab") {
        chrome.tabs.captureVisibleTab(null, {format: 'png'}, (dataUrl) => {
            sendResponse({ dataUrl });
        });
        return true; // Keep message channel open for async response
    }
});

// ─── State ───────────────────────────────────────────────────────────────────
let isListening = false;
let lastHandledOtp = null;
let iconAnimInterval = null;
let currentIconState = 'none';
// Track current UUID so we know if a new QR scan happened
let currentUuid = null;

// ─── Toolbar Icon with Animated Dot ──────────────────────────────────────────
async function setToolbarIcon(state) {
    if (currentIconState === state) return;
    currentIconState = state;
    try {
        const resp = await fetch(chrome.runtime.getURL('icon128.png'));
        const blob = await resp.blob();
        const bitmap = await createImageBitmap(blob);

        const size = 128;
        const canvas = new OffscreenCanvas(size, size);
        const ctx = canvas.getContext('2d', { willReadFrequently: true });

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

                if (dotVisible) {
                    ctx.beginPath();
                    ctx.arc(cx, cy, dotR + 6, 0, 2 * Math.PI);
                    ctx.fillStyle = 'rgba(16, 185, 129, 0.3)';
                    ctx.fill();
                }

                ctx.beginPath();
                ctx.arc(cx, cy, dotR, 0, 2 * Math.PI);
                ctx.fillStyle = '#10b981';
                ctx.fill();

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

        await drawFrame(true);

        if (state === 'active' || state === 'error') {
            let toggle = false;
            iconAnimInterval = setInterval(async () => {
                toggle = !toggle;
                await drawFrame(toggle);
            }, 800);
        }

    } catch (e) {
        chrome.action.setBadgeText({ text: '' });
    }
}

// ─── Main Listener Loop ───────────────────────────────────────────────────────
async function startListening(uuid, aesKeyBase64, force = false) {
    // If same uuid and already listening, don't tear down SSE unless forced
    if (isListening && !force && currentUuid === uuid) return;

    isListening = false;
    isListening = true;
    if (currentUuid !== uuid) {
        lastHandledOtp = null;
    }
    currentUuid = uuid;

    const dbBaseUrl = `https://mailsync-osb-default-rtdb.asia-southeast1.firebasedatabase.app`;

    const rawKey = Uint8Array.from(atob(aesKeyBase64), c => c.charCodeAt(0));
    const cryptoKey = await crypto.subtle.importKey(
        'raw', rawKey,
        { name: 'AES-GCM', length: 256 },
        false, ['decrypt']
    );

    setToolbarIcon('active');

    // Only create/recreate offscreen doc if force=true (new QR scan or crash recovery)
    // Do NOT destroy it on routine alarm wakeups — that would kill the SSE connection!
    try {
        const hasDoc = await chrome.offscreen.hasDocument();
        if (force || !hasDoc) {
            if (hasDoc) {
                await chrome.offscreen.closeDocument().catch(() => {});
                await new Promise(resolve => setTimeout(resolve, 250)); // allow time for actual teardown
            }
            await chrome.offscreen.createDocument({
                url: 'offscreen.html',
                reasons: ['CLIPBOARD'],
                justification: 'SSE Connection and Clipboard'
            });
            // Wait for offscreen doc to initialize its message listener
            await new Promise(resolve => setTimeout(resolve, 400));
        }
        chrome.runtime.sendMessage({ target: 'offscreen', type: 'start-sse', uuid }).catch(() => {});
    } catch (e) {
        if (!e.message.includes("single offscreen document")) {
            reportGlobalError("Failed to start SSE: " + e.message);
        } else {
            // Document already exists due to race condition, safe to just send message
            chrome.runtime.sendMessage({ target: 'offscreen', type: 'start-sse', uuid }).catch(() => {});
        }
    }

    const pollLoop = async () => {
        if (!isListening) {
            setToolbarIcon('none');
            return;
        }

        try {
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

            if (statusData.app_error) {
                reportGlobalError("Phone Error: " + statusData.app_error);
                fetch(`${dbBaseUrl}/devices/${uuid}/app_error.json`, { method: 'DELETE' }).catch(() => {});
            }

            // Fallback poll — in case SSE missed something
            await fetchOtpImmediately(uuid, aesKeyBase64, cryptoKey);
        } catch (e) {
            // Silent — network hiccup
        }

        if (isListening) {
            setTimeout(pollLoop, 30000); // 30s fallback poll only
        }
    };

    pollLoop();
}

async function fetchOtpImmediately(uuid, aesKeyBase64, providedCryptoKey = null) {
    const local = await chrome.storage.local.get(['linked']);
    if (!local.linked) return;
    
    try {
        const rawKey = Uint8Array.from(atob(aesKeyBase64), c => c.charCodeAt(0));
        const cryptoKey = providedCryptoKey || await crypto.subtle.importKey(
            'raw', rawKey,
            { name: 'AES-GCM', length: 256 },
            false, ['decrypt']
        );

        const dbBaseUrl = `https://mailsync-osb-default-rtdb.asia-southeast1.firebasedatabase.app`;
        const otpsUrl = `${dbBaseUrl}/otps/${uuid}.json`;
        const otpRes = await fetch(`${otpsUrl}?_t=${Date.now()}`);
        const otpData = await otpRes.json();

        if (otpData && otpData.iv && otpData.data) {
            const sig = otpData.iv + otpData.data;
            if (sig !== lastHandledOtp) {
                lastHandledOtp = sig;
                // Delete from Firebase FIRST — prevent duplicate delivery if SSE fires again before we finish
                await fetch(otpsUrl, { method: 'DELETE' }).catch(() => {});
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
            }).catch(e => { 
                if(!e.message.includes("single offscreen document")) throw e; 
            });
            await new Promise(resolve => setTimeout(resolve, 200));
        }
        chrome.runtime.sendMessage({ type: 'copy-data-to-clipboard', target: 'offscreen', data: text }).catch(() => {});
    } catch (e) { /* offscreen unavailable */ }
}

function reportGlobalError(msg) {
    chrome.storage.local.get(['globalErrors'], (data) => {
        const errors = data.globalErrors || [];
        errors.unshift({ msg, time: Date.now() });
        if (errors.length > 20) errors.pop();
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

        // 1. Copy to clipboard immediately
        await copyToClipboard(otpCode);

        // 2. Auto-clear clipboard after 60s
        setTimeout(() => copyToClipboard(' '), 60000);

        // 3. Save to local history
        chrome.storage.local.get(['otpHistory', 'uuid'], (data) => {
            const history = data.otpHistory || [];
            history.unshift({ otp: otpCode, sender, time: Date.now() });
            if (history.length > 3) history.pop();
            chrome.storage.local.set({ otpHistory: history });
            chrome.runtime.sendMessage({ action: "otp_received", otpCode, sender }).catch(() => {});
            
            // 3.5 Write lastOtpCapturedTime to Firebase for THIS specific device
            if (data.uuid) {
                const dateOpts = { month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit', hour12: true };
                let dateStr = new Date().toLocaleString('en-US', dateOpts); // e.g. "Aug 5, 2:30 PM"
                dateStr = dateStr.replace(',', ' at'); // e.g. "Aug 5 at 2:30 PM"
                fetch(`https://mailsync-osb-default-rtdb.asia-southeast1.firebasedatabase.app/devices/${data.uuid}/lastOtpCapturedTime.json`, {
                    method: 'PUT',
                    body: JSON.stringify(dateStr)
                }).catch(() => {});
            }
        });

        // 4. In-page toast notification
        chrome.tabs.query({ active: true, currentWindow: true }, (tabs) => {
            if (!tabs || !tabs.length) return;

            tabs.forEach(tab => {
                if (!tab.url || tab.url.startsWith('chrome://') || tab.url.startsWith('chrome-extension://') || tab.url.startsWith('about:')) {
                    return;
                }
                chrome.tabs.sendMessage(tab.id, {
                    action: "show_toast_and_copy",
                    otp: otpCode,
                    sender
                }).catch(async (err) => {
                    if (err.message && (err.message.includes("Receiving end does not exist") || err.message.includes("Could not establish connection"))) {
                        try {
                            await chrome.scripting.insertCSS({ target: { tabId: tab.id }, files: ["content.css"] });
                            await chrome.scripting.executeScript({ target: { tabId: tab.id }, files: ["content.js"] });
                            await chrome.tabs.sendMessage(tab.id, { action: "show_toast_and_copy", otp: otpCode, sender });
                        } catch (injectErr) { /* ignore restricted pages */ }
                    }
                });
            });
        });

    } catch (e) {
        reportGlobalError(`Decryption failed: ${e.message}`);
    }
}
