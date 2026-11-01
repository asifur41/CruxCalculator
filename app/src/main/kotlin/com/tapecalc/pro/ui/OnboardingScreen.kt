package com.tapecalc.pro.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private class OnboardingPage(val icon: ImageVector, val title: String, val body: String)

private val ONBOARDING_PAGES = listOf(
    OnboardingPage(
        Icons.Outlined.Calculate,
        "Welcome to Crux Calculator",
        "The private, ad-free calculator for people who actually calculate.\n\n" +
            "No ads, no tracking, no account and no internet permission. Everything stays on your phone."
    ),
    OnboardingPage(
        Icons.Outlined.Science,
        "Basic and scientific",
        "Swipe left and right, or use the bottom bar, to move between Basic, Scientific, Convert and Tape.\n\n" +
            "Many keys have a hidden second function: hold ⌫ for clear entry, hold × for powers, " +
            "hold 0 for 00, and hold ans to paste from the clipboard."
    ),
    OnboardingPage(
        Icons.Outlined.EditNote,
        "A tape you can edit",
        "Every calculation is saved on the Tape tab. Tap any entry to change it, add a label, a note or #tags.\n\n" +
            "Later results that use ans or a variable update automatically, or are marked stale until you recalculate."
    ),
    OnboardingPage(
        Icons.Outlined.SwapHoriz,
        "Variables and conversions",
        "Type rate = 0.18 once and reuse rate in any expression.\n\n" +
            "Convert units offline in the Convert tab, or type 12 km to mi straight into the calculator. " +
            "The Number system chip converts binary, octal, decimal and hexadecimal."
    ),
    OnboardingPage(
        Icons.Outlined.Palette,
        "Make it yours",
        "Open Settings (the gear icon) for 12 themes, a custom accent colour, key styles, " +
            "left-handed layout, haptic strength and text size.\n\n" +
            "You can replay this tutorial any time from Settings."
    )
)

/**
 * One-time swipeable introduction shown on the first launch. [onFinish] is called by "Get Started"
 * and by "Skip"; the caller remembers that it was seen.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onFinish: () -> Unit, modifier: Modifier = Modifier) {
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { ONBOARDING_PAGES.size })
    val scope = rememberCoroutineScope()
    val isLast = pagerState.currentPage == ONBOARDING_PAGES.lastIndex

    BackHandler(enabled = pagerState.currentPage > 0) {
        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
    ) {
        Row(Modifier.fillMaxWidth().height(56.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            if (!isLast) {
                TextButton(onClick = onFinish, modifier = Modifier.padding(end = 8.dp)) { Text("Skip") }
            }
        }

        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { index ->
            val page = ONBOARDING_PAGES[index]
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(128.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        page.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp)
                    )
                }
                Spacer(Modifier.height(32.dp))
                Text(
                    page.title,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    page.body,
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .semantics { contentDescription = "Page ${pagerState.currentPage + 1} of ${ONBOARDING_PAGES.size}" },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(ONBOARDING_PAGES.size) { i ->
                val active = i == pagerState.currentPage
                Box(
                    Modifier
                        .padding(horizontal = 4.dp)
                        .size(width = if (active) 24.dp else 8.dp, height = 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (active) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                        )
                )
            }
        }

        Button(
            onClick = {
                if (isLast) onFinish()
                else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp).height(56.dp)
        ) {
            Text(if (isLast) "Get Started" else "Next", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(24.dp))
    }
}
