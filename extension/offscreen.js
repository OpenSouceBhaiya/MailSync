let otpSse = null;
let deviceSse = null;

chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
    if (message.target !== 'offscreen') {
        return false;
    }

    if (message.type === 'start-sse') {
        const otpUrl = `https://mailsync-osb-default-rtdb.asia-southeast1.firebasedatabase.app/otps/${message.uuid}.json`;
        const deviceUrl = `https://mailsync-osb-default-rtdb.asia-southeast1.firebasedatabase.app/devices/${message.uuid}.json`;

        // 1. Setup OTP SSE
        if (otpSse && otpSse.url !== otpUrl) { otpSse.close(); otpSse = null; }
        if (!otpSse) {
            otpSse = new EventSource(otpUrl);
            let otpSseReady = false; // Skip the first (initial snapshot) put event to avoid false triggers
            otpSse.addEventListener('put', (e) => {
                if (!otpSseReady) {
                    // The very first 'put' is Firebase's initial data snapshot — parse it but only
                    // notify if there is ALREADY an OTP waiting (e.g. after service worker restart)
                    otpSseReady = true;
                    try {
                        const parsed = JSON.parse(e.data);
                        // parsed.data is the OTP object or null
                        if (parsed && parsed.data && parsed.data.iv && parsed.data.data) {
                            notifyBg('sse_otp');
                        }
                    } catch (_) {}
                    return;
                }
                try {
                    const parsed = JSON.parse(e.data);
                    // Only trigger if this is a real OTP write (not a deletion/null)
                    if (parsed && parsed.data && parsed.data.iv && parsed.data.data) {
                        notifyBg('sse_otp');
                    }
                } catch (_) {}
            });
            otpSse.addEventListener('patch', (e) => {
                try {
                    const parsed = JSON.parse(e.data);
                    if (parsed && parsed.data && (parsed.data.iv || parsed.data.data)) {
                        notifyBg('sse_otp');
                    }
                } catch (_) {}
            });
            otpSse.onerror = () => {};
        }

        // 2. Setup Device Status SSE
        if (deviceSse && deviceSse.url !== deviceUrl) { deviceSse.close(); deviceSse = null; }
        if (!deviceSse) {
            deviceSse = new EventSource(deviceUrl);
            let deviceSseReady = false;
            deviceSse.addEventListener('put', (e) => {
                if (!deviceSseReady) { deviceSseReady = true; return; } // Skip initial snapshot
                if (e.data) notifyBg('sse_device');
            });
            deviceSse.addEventListener('patch', (e) => {
                if (e.data) notifyBg('sse_device');
            });
            deviceSse.onerror = () => {};
        }

        sendResponse({ success: true });
        return true;
    }

    if (message.type === 'copy-data-to-clipboard') {
        const textToCopy = message.data;
        
        // Modern approach first (supported in offscreen in MV3)
        if (navigator.clipboard && navigator.clipboard.writeText) {
            navigator.clipboard.writeText(textToCopy)
                .then(() => {
                    chrome.runtime.sendMessage({ target: 'background', type: 'copy-complete' }).catch(() => {});
                    sendResponse({ success: true });
                })
                .catch(() => {
                    // Fallback to execCommand if modern fails
                    const textEl = document.getElementById('copy-area');
                    textEl.value = textToCopy;
                    textEl.select();
                    document.execCommand('copy');
                    chrome.runtime.sendMessage({ target: 'background', type: 'copy-complete' }).catch(() => {});
                    sendResponse({ success: true });
                });
            return true; // async response
        } else {
            const textEl = document.getElementById('copy-area');
            textEl.value = textToCopy;
            textEl.select();
            document.execCommand('copy');

            chrome.runtime.sendMessage({ target: 'background', type: 'copy-complete' }).catch(() => {});
            sendResponse({ success: true });
            return true;
        }
    }
});

function notifyBg(action) {
    chrome.runtime.sendMessage({ target: 'background', action: action }).catch(() => {});
}
