let otpSse = null;
let deviceSse = null;

chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
    if (message.target !== 'offscreen') {
        return false;
    }

    if (message.type === 'start-sse') {
        const otpUrl = `https://mailsync-relay-default-rtdb.asia-southeast1.firebasedatabase.app/otps/${message.uuid}.json`;
        const deviceUrl = `https://mailsync-relay-default-rtdb.asia-southeast1.firebasedatabase.app/devices/${message.uuid}.json`;
        
        // 1. Setup OTP SSE
        if (otpSse && otpSse.url !== otpUrl) { otpSse.close(); otpSse = null; }
        if (!otpSse) {
            otpSse = new EventSource(otpUrl);
            otpSse.addEventListener('put', (e) => { if (e.data && e.data !== "null") notifyBg('sse_otp'); });
            otpSse.addEventListener('patch', (e) => { if (e.data && e.data !== "null") notifyBg('sse_otp'); });
            otpSse.onerror = () => {};
        }

        // 2. Setup Device Status SSE
        if (deviceSse && deviceSse.url !== deviceUrl) { deviceSse.close(); deviceSse = null; }
        if (!deviceSse) {
            deviceSse = new EventSource(deviceUrl);
            deviceSse.addEventListener('put', (e) => { if (e.data) notifyBg('sse_device'); });
            deviceSse.addEventListener('patch', (e) => { if (e.data) notifyBg('sse_device'); });
            deviceSse.onerror = () => {};
        }

        sendResponse({ success: true });
        return true;
    }

    if (message.type === 'copy-data-to-clipboard') {
        const textToCopy = message.data;
        const textEl = document.getElementById('copy-area');
        textEl.value = textToCopy;
        textEl.select();
        document.execCommand('copy');
        
        chrome.runtime.sendMessage({ target: 'background', type: 'copy-complete' }).catch(() => {});
        sendResponse({ success: true });
        return true;
    }
});

function notifyBg(action) {
    chrome.runtime.sendMessage({ target: 'background', action: action }).catch(() => {});
}


