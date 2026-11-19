@file:OptIn(ExperimentalComposeUiApi::class)

package com.tapecalc.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.platform.PlatformTextInputSession
import kotlinx.coroutines.awaitCancellation

/*
 * The only place that touches the experimental Compose API for keeping the phone's own keyboard away.
 *
 * Every text box inside [WithoutSystemKeyboard] asks Android for a keyboard through a "text input session"; the
 * interceptor below takes that request and never passes it on, so the phone's keyboard does not open. The box itself
 * keeps working: the cursor, selection and the key presses of a connected hardware keyboard are handled by Compose.
 *
 * If a future Compose update renames or removes this API, only this file needs to change.
 */
private object NoSystemKeyboard : PlatformTextInputInterceptor {
    override suspend fun interceptStartInputMethod(
        request: PlatformTextInputMethodRequest,
        nextHandler: PlatformTextInputSession
    ): Nothing = awaitCancellation()
}

/** Draws [content] so that the text boxes in it do not open the phone's keyboard. */
@Composable
internal fun WithoutSystemKeyboard(content: @Composable () -> Unit) {
    InterceptPlatformTextInput(interceptor = NoSystemKeyboard, content = content)
}
