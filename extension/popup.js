// popup.js — MailSync Extension

function generateUUID() { return crypto.randomUUID(); }

function generateAESKey() {
    const array = new Uint8Array(32);
    crypto.getRandomValues(array);
    let binary = '';
    for (let i = 0; i < array.byteLength; i++) binary += String.fromCharCode(array[i]);
    return btoa(binary);
}

function timeAgo(ts) {
    const diff = Math.floor((Date.now() - ts) / 1000);
    if (diff < 60) return `${diff}s ago`;
    if (diff < 3600) return `${Math.floor(diff / 60)}m ago`;
    return `${Math.floor(diff / 3600)}h ago`;
}

// ─── Wave Emoji Animation ────────────────────────────────────────────────────
// Noto Animated Emoji: waving hand 👋
const WAVE_GIF_URL = 'https://fonts.gstatic.com/s/e/notoemoji/latest/1f44b/512.gif';
// Duration of the wave GIF (Noto wave hand ≈ 2s loop, we show it for one cycle)
const WAVE_DURATION_MS = 2000;

let waveTimeout = null;

function playWave() {
    const staticEl = document.getElementById('wave-static');
    const animEl = document.getElementById('wave-animated');
    if (!staticEl || !animEl) return;

    // Load the GIF lazily on first wave
    if (!animEl.src || animEl.src === window.location.href) {
        animEl.src = WAVE_GIF_URL;
    }

    // Show animated, hide static
    staticEl.style.opacity = '0';
    animEl.classList.add('playing');

    // Clear any previous timer
    if (waveTimeout) clearTimeout(waveTimeout);

    // After one cycle, hide animated and show static again
    waveTimeout = setTimeout(() => {
        animEl.classList.remove('playing');
        staticEl.style.opacity = '1';
    }, WAVE_DURATION_MS);
}

// ─── Time Greeting ────────────────────────────────────────────────────────
function renderGreeting(nameOverride) {
    const timeEl = document.getElementById('greeting-time');
    const nameEl = document.getElementById('greeting-name');
    if (!timeEl) return;

    const hour = new Date().getHours();
    const timeGreet = hour < 12 ? 'Good Morning' : hour < 17 ? 'Good Afternoon' : 'Good Evening';

    function setName(name) {
        if (name) {
            timeEl.textContent = timeGreet + ",";
            nameEl.textContent = name;
            nameEl.style.display = 'block';
        } else {
            timeEl.textContent = timeGreet;
            nameEl.style.display = 'none';
        }
    }

    if (nameOverride !== undefined) {
        setName(nameOverride);
        return;
    }
    chrome.storage.local.get(['userName'], (data) => {
        setName(data.userName || null);
    });
}

// ─── OTP History ──────────────────────────────────────────────────────────────
function renderOtpHistory() {
    chrome.storage.local.get(['otpHistory'], (data) => {
        const THIRTY_MIN = 30 * 60 * 1000;
        const history = (data.otpHistory || []).filter(item => Date.now() - item.time < THIRTY_MIN);
        chrome.storage.local.set({ otpHistory: history });

        const container = document.getElementById('otp-history-list');
        const section = document.getElementById('otp-history-section');
        if (!container || !section) return;

        if (history.length === 0) { section.classList.add('hidden'); return; }
        section.classList.remove('hidden');
        container.innerHTML = '';

        history.forEach(item => {
            const div = document.createElement('div');
            div.className = 'otp-history-item';

            const clipboardSvg = `<svg class="otp-copy-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#8E8A9F" stroke-width="2"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path></svg>`;

            div.innerHTML = `
                <div style="display:flex;justify-content:space-between;align-items:center;">
                    <div class="otp-history-code">${item.otp}</div>
                    <div class="otp-copy-btn">${clipboardSvg}</div>
                </div>
                <div class="otp-history-meta"><span class="otp-history-sender">${item.sender}</span> · <span>${timeAgo(item.time)}</span></div>
            `;

            div.addEventListener('click', () => {
                // Copy natively within the popup
                navigator.clipboard.writeText(item.otp).catch(() => {});
                const btn = div.querySelector('.otp-copy-btn');
                const checkSvg = `<svg class="otp-copy-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#00FFA3" stroke-width="2.5"><polyline points="20 6 9 17 4 12"></polyline></svg>`;
                btn.innerHTML = checkSvg;
                setTimeout(() => { btn.innerHTML = clipboardSvg; }, 2000);
            });
            container.appendChild(div);
        });
    });
}

// ─── Global Error Log ─────────────────────────────────────────────────────────
function updateGlobalErrors() {
    chrome.storage.local.get(['globalErrors'], (data) => {
        const errors = data.globalErrors || [];
        const section = document.getElementById('error-log-section');
        const container = document.getElementById('error-log-list');
        const bugHint = document.getElementById('bug-report-hint');
        if (!section || !container) return;

        if (errors.length === 0) {
            section.classList.add('hidden');
            if (bugHint) bugHint.classList.add('hidden');
            return;
        }
        section.classList.remove('hidden');
        if (bugHint) bugHint.classList.remove('hidden'); // Show "copy errors before reporting"
        container.innerHTML = '';
        errors.forEach(err => {
            const div = document.createElement('div');
            div.className = 'error-log-item';
            div.innerHTML = `<span class="error-log-time">${new Date(err.time).toLocaleTimeString()}</span><span class="error-log-msg">${err.msg}</span>`;
            container.appendChild(div);
        });
    });
}

// ─── Device Name Detection ────────────────────────────────────────────────────
async function getDeviceName() {
    // Try high-entropy UA data first (gives model on some devices)
    if (navigator.userAgentData && navigator.userAgentData.getHighEntropyValues) {
        try {
            const ua = await navigator.userAgentData.getHighEntropyValues(['model', 'platform', 'platformVersion']);
            if (ua.model && ua.model.length > 0 && ua.model !== '') {
                return ua.model; // e.g. "HP EliteBook 840"
            }
            // Fallback to platform
            const os = ua.platform || '';
            if (os.toLowerCase().includes('windows')) return 'Windows PC';
            if (os.toLowerCase().includes('mac')) return 'Mac';
            if (os.toLowerCase().includes('chromeos')) return 'Chrome OS';
            if (os.toLowerCase().includes('linux')) return 'Linux PC';
        } catch (e) { console.warn("Failed to get high entropy user agent:", e); }
    }
    // Legacy UA string fallback
    const ua = navigator.userAgent;
    if (ua.includes('Win')) return 'Windows PC';
    if (ua.includes('Mac')) return 'Mac';
    if (ua.includes('CrOS')) return 'Chrome OS';
    if (ua.includes('Linux')) return 'Linux PC';
    return 'PC';
}

// ─── Main ─────────────────────────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', async () => {
    // Clear stale errors (only keep last 10 minutes)
    chrome.storage.local.get(['globalErrors'], (data) => {
        const TEN_MIN = 10 * 60 * 1000;
        const fresh = (data.globalErrors || []).filter(e => Date.now() - e.time < TEN_MIN);
        chrome.storage.local.set({ globalErrors: fresh }, () => updateGlobalErrors());
    });

    // Wire up wave emoji button
    const waveBtn = document.getElementById('wave-btn');
    if (waveBtn) {
        waveBtn.addEventListener('click', playWave);
    }

    // Wire up Run Diagnostics button (removed - not public facing)

    // Wire up error copy button
    const copyErrBtn = document.getElementById('copy-errors-btn');
    if (copyErrBtn) {
        copyErrBtn.addEventListener('click', () => {
            chrome.storage.local.get(['globalErrors'], (data) => {
                const errors = data.globalErrors || [];
                if (errors.length === 0) {
                    copyErrBtn.textContent = '❌ No errors';
                    setTimeout(() => { copyErrBtn.textContent = '📋 Copy'; }, 2000);
                    return;
                }
                const text = errors.map(e => `[${new Date(e.time).toLocaleTimeString()}] ${e.msg}`).join('\n');
                navigator.clipboard.writeText(text).then(() => {
                    copyErrBtn.textContent = '✅ Copied!';
                    setTimeout(() => { copyErrBtn.textContent = '📋 Copy'; }, 2000);
                }).catch(() => {});
            });
        });
    }

    // Wire up error clear button
    const clearErrBtn = document.getElementById('clear-errors-btn');
    if (clearErrBtn) {
        clearErrBtn.addEventListener('click', () => {
            chrome.storage.local.set({ globalErrors: [] }, () => updateGlobalErrors());
        });
    }

    // Offline indicator
    const offlineIndicator = document.getElementById('offline-indicator');
    function updateOnlineStatus() {
        if (navigator.onLine) {
            offlineIndicator.classList.add('hidden');
            document.body.classList.remove('is-offline');
        } else {
            offlineIndicator.classList.remove('hidden');
            document.body.classList.add('is-offline');
        }
    }
    window.addEventListener('online', updateOnlineStatus);
    window.addEventListener('offline', updateOnlineStatus);
    updateOnlineStatus();

    // Listen for real-time messages from background
    chrome.runtime.onMessage.addListener((message) => {
        if (message.action === "device_terminated") {
            showTerminatedView();
        } else if (message.action === "status_update" && message.statusData) {
            applyStatusData(message.statusData);
        } else if (message.action === "otp_received") {
            renderOtpHistory();
        } else if (message.action === "global_error_updated") {
            updateGlobalErrors();
        }
    });

    const reloginBtn = document.getElementById('relogin-btn');
    const unlinkBtns = [
        document.getElementById('unlink-btn'),
        document.getElementById('unlink-btn-no-account'),
        document.getElementById('unlink-btn-paused')
    ];

    // ─── Initial State Check ──────────────────────────────────────────────────
    chrome.storage.local.get(['uuid', 'aesKey', 'linked', 'qrExpiresAt', 'userName', 'terminated'], async (data) => {
        if (data.terminated) {
            // User explicitly unlinked — show terminated view, not QR
            showTerminatedView();
        } else if (data.linked && data.uuid) {
            // Show name immediately from cache, then update when status arrives
            showLinkedState(data.userName || null);
            startStatusLoop(data.uuid);
        } else {
            startQRFlow(data);
        }
    });

    // ─── Unlink Button ────────────────────────────────────────────────────────
    unlinkBtns.forEach(btn => {
        if (btn) {
            btn.addEventListener('click', () => {
                const modal = document.getElementById('confirm-modal');
                const cancelBtn = document.getElementById('modal-cancel-btn');
                const confirmBtn = document.getElementById('modal-confirm-btn');
                modal.classList.remove('hidden');
                cancelBtn.onclick = () => modal.classList.add('hidden');
                confirmBtn.onclick = async () => {
                    modal.classList.add('hidden');
                    const data = await chrome.storage.local.get(['uuid']);
                    if (data.uuid) {
                        try {
                            await fetch(`https://mailsync-osb-default-rtdb.asia-southeast1.firebasedatabase.app/devices/${data.uuid}.json`, {
                                method: 'PATCH',
                                headers: { 'Content-Type': 'application/json' },
                                body: JSON.stringify({ status: "terminated", syncEnabled: false })
                            });
                        } catch (e) { console.warn("Failed to notify Firebase of termination:", e); }
                    }
                    await chrome.storage.local.clear();
                    // Mark as terminated so next popup open shows Session Ended, not QR
                    await chrome.storage.local.set({ terminated: true });
                    try {
                        chrome.runtime.sendMessage({ action: "stop_listening" }).catch(() => {});
                    } catch(e) { console.warn("Failed to send stop_listening message:", e); }
                    window.location.reload();
                };
            });
        }
    });

    // ─── Re-login Button ──────────────────────────────────────────────────────
    reloginBtn.addEventListener('click', async () => {
        chrome.runtime.sendMessage({ action: "stop_listening" }).catch(() => {});
        await chrome.storage.local.clear();
        window.location.reload();
    });

    // ─── Status Loop ──────────────────────────────────────────────────────────
    function startStatusLoop(uuid) {
        checkDeviceStatus(uuid);
    }

    async function checkDeviceStatus(uuid) {
        try {
            const res = await fetch(`https://mailsync-osb-default-rtdb.asia-southeast1.firebasedatabase.app/devices/${uuid}.json?_t=${Date.now()}`);
            const data = await res.json();
            if (!data || !data.dateLinked) {
                showTerminatedView();
                return;
            }
            applyStatusData(data);
        } catch (e) { console.warn("Device status check failed:", e); }
        setTimeout(() => checkDeviceStatus(uuid), 2500);
    }

    function applyStatusData(data) {
        // Update name from device data
        const nameToShow = data.accountName || null;
        chrome.storage.local.set({ userName: nameToShow });

        const statusIndicator = document.getElementById('statusIndicator');
        const statusText = document.getElementById('statusText');
        const pausedSubtitle = document.getElementById('paused-subtitle');

        // If status is terminated or syncEnabled is false AND status is terminated, go direct to terminated view
        if (data.status === 'terminated') {
            showTerminatedView();
            return;
        }

        if (data.status === 'error_no_accounts') {
            const linkedView = document.getElementById('linked-view');
            if (linkedView.classList.contains('hidden')) {
                showLinkedState(nameToShow);
            } else {
                renderGreeting(nameToShow);
            }
            if (statusIndicator) statusIndicator.className = 'status-dot-container failing';
            if (statusText) statusText.textContent = "No Accounts Linked";
        } else if (data.syncEnabled === false) {
            hideAll();
            document.getElementById('paused-view').classList.remove('hidden');
            renderGreeting(nameToShow);
            if (pausedSubtitle) pausedSubtitle.textContent = "Sync is paused. Enable the master sync switch in the MailSync Android app.";
            if (statusIndicator) statusIndicator.className = 'status-dot-container failing';
            if (statusText) statusText.textContent = "Sync Paused";
        } else if (data.status === 'paused') {
            hideAll();
            document.getElementById('paused-view').classList.remove('hidden');
            renderGreeting(nameToShow);
            if (pausedSubtitle) pausedSubtitle.textContent = "Sync is paused. Enable \"Instant Sync Engine\" in MailSync Android app.";
            if (statusIndicator) statusIndicator.className = 'status-dot-container failing';
            if (statusText) statusText.textContent = "Sync Paused";
        } else {
            const linkedView = document.getElementById('linked-view');
            if (linkedView.classList.contains('hidden')) {
                showLinkedState(nameToShow);
            } else {
                // Already showing linked view, just update the name
                renderGreeting(nameToShow);
            }
            if (statusIndicator) statusIndicator.className = 'status-dot-container working';
            if (statusText) statusText.textContent = "System Active";
        }

        renderOtpHistory();
    }

    function showLinkedState(nameOverride) {
        hideAll();
        document.getElementById('linked-view').classList.remove('hidden');
        renderGreeting(nameOverride);
        renderOtpHistory();
        // Play wave animation once when linked view is shown
        setTimeout(playWave, 300);
    }

    function showTerminatedView() {
        hideAll();
        document.getElementById('terminated-view').classList.remove('hidden');
        chrome.runtime.sendMessage({ action: "stop_listening" });
    }

    function hideAll() {
        ['linked-view', 'terminated-view', 'paused-view', 'no-accounts-view', 'unlinked-view'].forEach(id => {
            const el = document.getElementById(id);
            if (el) el.classList.add('hidden');
        });
    }

    // ─── QR Flow ──────────────────────────────────────────────────────────────
    async function startQRFlow(data) {
        document.getElementById('unlinked-view').classList.remove('hidden');

        let uuid = data.uuid;
        let aesKey = data.aesKey;
        let qrExpiresAt = data.qrExpiresAt;
        const now = Date.now();

        if (!uuid || !aesKey || !qrExpiresAt || now >= qrExpiresAt) {
            uuid = generateUUID();
            aesKey = generateAESKey();
            qrExpiresAt = now + (180 * 1000); // 3 minutes
            await chrome.storage.local.set({ uuid, aesKey, qrExpiresAt, linked: false });
        }

        async function renderQR(u, k) {
            // Get real device name (may include model like "HP EliteBook") or custom name
            let pcName = data.userName;
            if (!pcName) {
                pcName = await getDeviceName();
            }

            const qrData = `https://opensourcebhaiya.online/apps/mailsync/connect?uuid=${encodeURIComponent(u)}&name=${encodeURIComponent(pcName)}&browser=Chrome&key=${encodeURIComponent(k)}`;

            const canvas = document.getElementById('qr-code');
            new QRious({
                element: canvas,
                value: qrData,
                size: 232,
                level: 'M',
                padding: 0,
                background: '#FFFFFF',
                foreground: '#000000'
            });
        }

        await renderQR(uuid, aesKey);

        const timerText = document.getElementById('timer-text');
        const timerContainer = document.querySelector('.qr-timer');

        function updateTimerUI() {
            let timeLeft = Math.max(0, Math.floor((qrExpiresAt - Date.now()) / 1000));
            const m = Math.floor(timeLeft / 60).toString().padStart(2, '0');
            const s = (timeLeft % 60).toString().padStart(2, '0');
            timerText.innerText = `${m}:${s}`;
            if (timeLeft <= 30) timerContainer.classList.add('expiring');
            else timerContainer.classList.remove('expiring');
            return timeLeft;
        }
        updateTimerUI();

        window.timerInterval = setInterval(async () => {
            let timeLeft = updateTimerUI();
            if (timeLeft <= 0) {
                // Before generating a new QR, check if phone already linked with current uuid
                // This handles the race condition where phone scans in the last second of a QR
                const currentData = await chrome.storage.local.get(['linked']);
                if (currentData.linked) {
                    clearInterval(window.timerInterval);
                    return; // Already linked — don't overwrite with a new QR
                }
                uuid = generateUUID();
                aesKey = generateAESKey();
                qrExpiresAt = Date.now() + (180 * 1000); // 3 minutes
                await chrome.storage.local.set({ uuid, aesKey, qrExpiresAt, linked: false });
                await renderQR(uuid, aesKey);
                clearInterval(pollInterval);
                pollForLink(uuid, aesKey);
            }
        }, 1000);

        pollForLink(uuid, aesKey);
    }

    let pollInterval;
    function pollForLink(uuid, aesKey) {
        const firebaseUrl = `https://mailsync-osb-default-rtdb.asia-southeast1.firebasedatabase.app/devices/${uuid}.json`;
        if (pollInterval) clearInterval(pollInterval);

        pollInterval = setInterval(async () => {
            try {
                const response = await fetch(firebaseUrl + `?_t=${Date.now()}`);
                const data = await response.json();

                if (data && data.dateLinked) {
                    clearInterval(pollInterval);
                    if (window.timerInterval) clearInterval(window.timerInterval);

                    await chrome.storage.local.set({ linked: true });
                    if (data.accountName) {
                        await chrome.storage.local.set({ userName: data.accountName });
                    }

                    // Immediately hide QR screen — don't wait for applyStatusData network call
                    hideAll();
                    document.getElementById('linked-view').classList.remove('hidden');
                    renderGreeting(data.accountName || null);
                    setTimeout(playWave, 300);

                    chrome.runtime.sendMessage({ action: "start_listening" });
                    applyStatusData(data);
                    startStatusLoop(uuid);
                }
            } catch (e) { console.warn("Polling for link failed:", e); }
        }, 1500);
    }
});
