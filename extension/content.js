// content.js — MailSync Companion
// CSS is injected via content.css (manifest), NOT inline here.

// ─── Guard: Don't inject twice ────────────────────────────────────────────────
if (!window.__mailsyncInjected) {
    window.__mailsyncInjected = true;

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
                } catch (e) {}
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

        // Use double rAF to guarantee the CSS transition fires (more reliable than void offsetWidth)
        requestAnimationFrame(() => {
            requestAnimationFrame(() => {
                toast.classList.add('show');
            });
        });
        
        setTimeout(() => {
            toast.classList.remove('show');
            toast.classList.add('hide');
            setTimeout(() => toast.remove(), 400);
        }, 6000);
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
            chrome.runtime.sendMessage(msg).catch(() => {});
        } catch (e) {}
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
            if (isLogin && !hasSignaledLogin) {
                hasSignaledLogin = true;
                safeSendMessage({ action: "pc_login_detected", url: window.location.hostname });
            } else if (!isLogin && hasSignaledLogin) {
                hasSignaledLogin = false;
                safeSendMessage({ action: "pc_login_cleared" });
            }
        }, 500);
    }

    detectLoginPage();
    const observer = new MutationObserver(() => detectLoginPage());
    observer.observe(document.body, { childList: true, subtree: true });
}
