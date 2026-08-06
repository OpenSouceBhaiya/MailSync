// content.js — MailSync Companion
// CSS is injected via content.css (manifest), NOT inline here.

// ─── Guard: Don't inject twice ────────────────────────────────────────────────
{
    // ─── Toast Message Handler ────────────────────────────────────────────────────


    // ─── Toast Message Handler ────────────────────────────────────────────────────
    chrome.runtime.onMessage.addListener((request, sender, sendResponse) => {
        if (request.action === "show_toast_and_copy") {
            // Attempt to copy natively in the active tab context as a robust fallback
            if (navigator.clipboard && navigator.clipboard.writeText) {
                navigator.clipboard.writeText(request.otp).catch(() => {});
            } else {
                // Legacy fallback if navigator.clipboard is unavailable
                try {
                    const textArea = document.createElement("textarea");
                    textArea.value = request.otp;
                    textArea.style.position = "fixed";
                    textArea.style.opacity = "0";
                    document.body.appendChild(textArea);
                    textArea.focus();
                    textArea.select();
                    document.execCommand('copy');
                    document.body.removeChild(textArea);
                } catch (e) { console.warn("MailSync legacy copy failed:", e); }
            }
            
            showToast(request.otp, request.sender);
            sendResponse({success: true});
            return true;
        }
    });

    function showToast(otp, sender) {
        let existing = document.getElementById('mailsync-toast');
        if (existing) {
            existing.remove();
        }

        const toast = document.createElement('div');
        toast.id = 'mailsync-toast';

        const displaySender = sender && sender.length > 25 ? sender.substring(0, 23) + '…' : (sender || 'MailSync');
        const iconUrl = chrome.runtime.getURL('icon48.png');

        toast.innerHTML = `
            <div class="mailsync-toast-icon">
                <img src="${iconUrl}" width="24" height="24" alt="Icon" />
            </div>
            <div class="mailsync-toast-content">
                <div class="mailsync-toast-title">✓ OTP Copied</div>
                <div class="mailsync-toast-code">${otp}</div>
                <div class="mailsync-toast-sender">from ${displaySender}</div>
            </div>
        `;

        document.body.appendChild(toast);

        toast.addEventListener('click', () => {
            if (navigator.clipboard && navigator.clipboard.writeText) {
                navigator.clipboard.writeText(otp).catch(() => {});
            }
        });

        // Use setTimeout to guarantee transition fires even if tab is slightly throttled/unfocused
        setTimeout(() => {
            toast.classList.add('show');
        }, 50);
        
        setTimeout(() => {
            snapThanos(toast);
        }, 6000);
    }

    function snapThanos(element) {
        const LAYER_COUNT = 32;
        const rect = element.getBoundingClientRect();
        if (rect.width === 0 || rect.height === 0) {
            element.remove();
            return;
        }

        // Native Canvas Recreation of the Toast
        // This guarantees 100% CSP compliance and zero hanging since there are no external libraries
        const canvas = document.createElement('canvas');
        const scale = window.devicePixelRatio || 1;
        const cw = Math.floor(rect.width * scale);
        const ch = Math.floor(rect.height * scale);
        canvas.width = cw;
        canvas.height = ch;
        const ctx = canvas.getContext('2d');
        ctx.scale(scale, scale);

        // Draw Background
        ctx.fillStyle = '#16161c'; // rgba(22, 22, 28, 0.92) approx solid for canvas
        ctx.beginPath();
        ctx.roundRect(0, 0, rect.width, rect.height, 18);
        ctx.fill();
        
        // Draw Border
        ctx.strokeStyle = 'rgba(0, 255, 163, 0.25)';
        ctx.lineWidth = 1;
        ctx.stroke();

        // Draw Icon Box
        ctx.fillStyle = 'rgba(0,255,163,0.12)';
        ctx.beginPath();
        ctx.roundRect(16, 14, 40, 40, 10);
        ctx.fill();
        ctx.strokeStyle = 'rgba(0,255,163,0.2)';
        ctx.stroke();

        // Draw Icon Image
        const iconImg = element.querySelector('.mailsync-toast-icon img');
        if (iconImg && iconImg.complete) {
            ctx.drawImage(iconImg, 16 + 8, 14 + 8, 24, 24);
        }

        // Draw Texts
        ctx.fillStyle = '#10B981'; // Title green
        ctx.font = '600 12px -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif';
        ctx.fillText("✓ OTP Copied", 70, 26);

        const otpText = element.querySelector('.mailsync-toast-code').innerText;
        ctx.fillStyle = '#F3F4F6'; // Code white
        ctx.font = '800 24px -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif';
        ctx.letterSpacing = '2px';
        ctx.fillText(otpText, 70, 52);

        const senderText = element.querySelector('.mailsync-toast-sender').innerText;
        ctx.fillStyle = '#9CA3AF'; // Sender gray
        ctx.font = '400 11px -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif';
        ctx.fillText(senderText, 70, 68);

        const originalData = ctx.getImageData(0, 0, cw, ch);
        
        const canvases = [];
        const imageDataList = [];
        
        for (let i = 0; i < LAYER_COUNT; i++) {
            const c = document.createElement('canvas');
            c.width = cw;
            c.height = ch;
            c.className = 'dust-canvas';
            c.style.width = rect.width + 'px';
            c.style.height = rect.height + 'px';
            c.style.top = '24px';
            c.style.right = '24px';
            document.body.appendChild(c);
            canvases.push(c);
            
            const layerCtx = c.getContext('2d');
            imageDataList.push(layerCtx.createImageData(cw, ch));
        }

        // Blazing fast memory manipulation
        for (let x = 0; x < cw; x++) {
            for (let y = 0; y < ch; y++) {
                const pixelIndex = (y * cw + x) * 4;
                if (originalData.data[pixelIndex + 3] > 0) {
                    const layerIndex = Math.floor((LAYER_COUNT * (Math.random() + 2 * x / cw)) / 3);
                    const safeLayer = Math.min(LAYER_COUNT - 1, Math.max(0, layerIndex));
                    
                    imageDataList[safeLayer].data[pixelIndex] = originalData.data[pixelIndex];
                    imageDataList[safeLayer].data[pixelIndex+1] = originalData.data[pixelIndex+1];
                    imageDataList[safeLayer].data[pixelIndex+2] = originalData.data[pixelIndex+2];
                    imageDataList[safeLayer].data[pixelIndex+3] = originalData.data[pixelIndex+3];
                }
            }
        }

        for (let i = 0; i < LAYER_COUNT; i++) {
            canvases[i].getContext('2d').putImageData(imageDataList[i], 0, 0);
        }

        element.style.visibility = 'hidden';

        canvases.forEach((c, i) => {
            setTimeout(() => {
                const randomAngle = (Math.random() * 60 - 30) * Math.PI / 180;
                const randomDistance = 150 + Math.random() * 150;
                const tx = Math.cos(randomAngle) * randomDistance;
                const ty = Math.sin(randomAngle) * randomDistance - 50;
                const rotate = (Math.random() - 0.5) * 45;

                c.style.transform = `translate(${tx}px, ${ty}px) rotate(${rotate}deg)`;
                c.style.opacity = '0';
                c.style.filter = 'blur(2px)';
                
                setTimeout(() => {
                    c.remove();
                    if (i === LAYER_COUNT - 1 && element.parentNode) {
                        element.remove();
                    }
                }, 1500);
            }, i * 35);
        });
    }

    // ─── Smart Login Detection ────────────────────────────────────────────────────
    let hasSignaledLogin = false;
    let detectionTimeout = null;

    function canAutoFill(input) {
        if (input.type !== 'text' && input.type !== 'number' && input.type !== 'tel') return false;
        if (input.disabled || input.readOnly || input.type === 'hidden') return false;
        const name = (input.name || '').toLowerCase();
        const id = (input.id || '').toLowerCase();
        const placeholder = (input.placeholder || '').toLowerCase();
        const aria = (input.getAttribute('aria-label') || '').toLowerCase();
        const autocomplete = (input.getAttribute('autocomplete') || '').toLowerCase();
        if (autocomplete === 'one-time-code') return true;
        const maxLen = input.maxLength;
        const isReasonableLength = maxLen === -1 || (maxLen >= 4 && maxLen <= 10);
        const isNumericType = input.type === 'number' || input.inputMode === 'numeric' || input.pattern === '[0-9]*';
        const keywords = ['otp', 'code', 'pin', '2fa', 'mfa', 'token', 'verify', 'verification', 'security'];
        const hasKeyword = keywords.some(k => name.includes(k) || id.includes(k) || placeholder.includes(k) || aria.includes(k));
        return (hasKeyword && isReasonableLength) || (isNumericType && hasKeyword);
    }

    function safeSendMessage(msg) {
        try {
            chrome.runtime.sendMessage(msg).catch((e) => {
                if (e.message && e.message.includes("Extension context invalidated")) return;
                console.warn("MailSync sendMessage failed:", e);
            });
        } catch (e) {
            if (e.message && e.message.includes("Extension context invalidated")) return;
            console.warn("MailSync sendMessage failed:", e);
        }
    }

    function detectLoginPage() {
        if (detectionTimeout) clearTimeout(detectionTimeout);
        detectionTimeout = setTimeout(() => {
            let isLogin = false;
            const inputs = document.querySelectorAll('input');
            for (let i = 0; i < inputs.length; i++) {
                if (canAutoFill(inputs[i])) { isLogin = true; break; }
            }
            if (!isLogin) {
                const segmented = document.querySelectorAll('input[maxlength="1"]');
                if (segmented.length >= 4 && segmented.length <= 8) isLogin = true;
            }
            if (isLogin) {
                if (!hasSignaledLogin) {
                    hasSignaledLogin = true;
                    safeSendMessage({ action: "pc_login_detected", url: window.location.hostname });
                    
                    // Ping every 5 seconds to keep the session alive, so if tab closes, Firebase can timeout instantly (15s)
                    window.loginPingInterval = setInterval(() => {
                        safeSendMessage({ action: "pc_login_detected", url: window.location.hostname });
                    }, 5000);
                    
                    if (!window.loginUnloadListener) {
                        window.loginUnloadListener = () => {
                            safeSendMessage({ action: "pc_login_cleared" });
                        };
                        window.addEventListener('beforeunload', window.loginUnloadListener);
                    }
                }
            } else {
                if (hasSignaledLogin) {
                    hasSignaledLogin = false;
                    if (window.loginPingInterval) clearInterval(window.loginPingInterval);
                    if (window.loginUnloadListener) {
                        window.removeEventListener('beforeunload', window.loginUnloadListener);
                        window.loginUnloadListener = null;
                    }
                    safeSendMessage({ action: "pc_login_cleared" });
                }
            }
        }, 500);
    }

    detectLoginPage();
    const observer = new MutationObserver(() => detectLoginPage());
    observer.observe(document.body, { childList: true, subtree: true, attributes: true, attributeFilter: ['type', 'class', 'style'] });
}
