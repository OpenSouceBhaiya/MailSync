package com.mailsync.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.with
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mailsync.app.ui.theme.DarkBackground
import com.mailsync.app.ui.theme.TextPrimary
import com.mailsync.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay

val TIPS = listOf(
    "Tip: Your OTPs never leave your device. We use a local-only encrypted database.",
    "Did you know? Gmail OTP Syncer uses the official Gmail API. No sketchy middleman servers.",
    "Tip: Enable Biometric Lock in settings to keep your OTPs secure from prying eyes.",
    "Tip: Turn on Privacy Mode to block screenshots of your sensitive OTPs.",
    "Productivity: Automate the small things, so you can focus on the big things.",
    "Quote: 'Simplicity is the ultimate sophistication.' - Leonardo da Vinci",
    "Did you know? You can long-press the app icon to start scanning instantly.",
    "Tip: Connecting multiple Gmail accounts? They all sync perfectly together.",
    "Quote: 'Time is what we want most, but what we use worst.' - William Penn",
    "Productivity: Eliminating 5 seconds of friction 100 times a day saves you hours.",
    "Tip: We filter out non-OTP emails entirely so your inbox stays clean.",
    "Did you know? Our advanced OTP extraction engine recognizes patterns locally.",
    "Quote: 'The advance of technology is based on making it fit in so that you don't really even notice it.' - Bill Gates",
    "Productivity: Small efficiencies compound over time.",
    "Quote: 'It's not that we use technology, we live technology.' - Godfrey Reggio",
    "Tip: MailSync runs entirely on your phone. No cloud storage for your private OTPs.",
    "Security: Real-time interception happens inside Android's secure memory boundary.",
    "Did you know? Most OTPs expire in 5 to 10 minutes. We clean up the expired ones.",
    "Quote: 'Focus is a matter of deciding what things you're not going to do.' - John Carmack",
    "Tip: You can manually mark an OTP as used by tapping the checkmark.",
    "Pro Tip: Keep the Instant Sync Engine enabled for the fastest possible OTP detection.",
    "Quote: 'Good design is obvious. Great design is transparent.' - Joe Sparano",
    "Fact: The average person types an OTP 4 times a week. That's time you just got back.",
    "Tip: Missing an OTP? Try pulling down to refresh the inbox manually.",
    "Quote: 'Security is a process, not a product.' - Bruce Schneier",
    "Did you know? Background Clipboard uses an invisible overlay to safely copy text on Android 10+.",
    "Productivity: Multitasking is a myth. Let us handle the OTP while you stay in your flow.",
    "Fact: We instantly scrub all sender names to remove clutter and keep your inbox beautiful.",
    "Tip: If an account stops syncing, try removing and adding it back in Settings.",
    "Quote: 'Any sufficiently advanced technology is indistinguishable from magic.' - Arthur C. Clarke",
    "Did you know? We analyze over 50 different email structures to find hidden OTPs.",
    "Tip: Need to copy the OTP again? Just tap the code itself in the inbox.",
    "Quote: 'Design is not just what it looks like and feels like. Design is how it works.' - Steve Jobs",
    "Fact: Your credentials are encrypted using military-grade AES-256 local encryption.",
    "Productivity: Don't break your concentration. The OTP is already on your clipboard.",
    "Tip: You can search your OTP history using the search bar at the top.",
    "Did you know? We strip tracking pixels from background synced emails to protect your privacy.",
    "Quote: 'Perfection is achieved, not when there is nothing more to add, but when there is nothing left to take away.' - Antoine de Saint-Exupery",
    "Security: The app never requests permission to send emails on your behalf.",
    "Fact: App loading speed is optimized by rendering UI elements before data finishes decrypting."
)

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun LoadingWithTips(modifier: Modifier = Modifier) {
    var tipIndex by remember { mutableStateOf(TIPS.indices.random()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(3500)
            tipIndex = (tipIndex + 1) % TIPS.size
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                text = "Syncing...",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            AnimatedContent(
                targetState = tipIndex,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(500)) + slideInVertically(
                        initialOffsetY = { 20 }, animationSpec = tween(500)
                    )) with (fadeOut(animationSpec = tween(500)) + slideOutVertically(
                        targetOffsetY = { -20 }, animationSpec = tween(500)
                    ))
                },
                label = "tip_animation"
            ) { targetIndex ->
                Text(
                    text = TIPS[targetIndex],
                    color = TextSecondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
