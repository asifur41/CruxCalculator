package com.tapecalc.pro.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.tapecalc.core.ui.KeyAction

/** True for the keys that change what happens next: "=", CALC and the SHIFT / ALPHA / HYP toggles. They feel firmer. */
fun isFirmKey(action: KeyAction): Boolean = when (action) {
    KeyAction.Equals, KeyAction.Calc, KeyAction.Shift, KeyAction.Alpha, KeyAction.Hyp -> true
    else -> false
}

/**
 * Key feedback. Haptics use Android's own haptic constants, so they feel like the phone's keyboard: KEYBOARD_TAP for
 * ordinary keys, VIRTUAL_KEY for "=" and the SHIFT / ALPHA toggles, and one step heavier for each when "Haptic Strong" is
 * on. The optional click is the system touch sound. Sound is a sub-option of haptics, so it is off whenever haptics are.
 * Nothing is recorded or sent anywhere.
 */
class Feedback(context: Context, private val view: View) {

    private val vibrator: Vibrator? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    } catch (e: Exception) {
        null
    }

    /** [firm] marks "=" and the state toggles ([isFirmKey]); [hapticLevel] is the saved level (see [HapticPlan]). */
    fun key(firm: Boolean, hapticLevel: Int, sound: Boolean) {
        if (!HapticPlan.isOn(hapticLevel)) return
        if (sound) view.playSoundEffect(SoundEffectConstants.CLICK)
        val tier = HapticPlan.tier(firm, HapticPlan.isStrong(hapticLevel))
        val constant = when (tier) {
            HapticTier.TAP -> HapticFeedbackConstants.KEYBOARD_TAP
            HapticTier.KEY -> HapticFeedbackConstants.VIRTUAL_KEY
            HapticTier.HEAVY -> HapticFeedbackConstants.LONG_PRESS
        }
        val done = try {
            view.performHapticFeedback(constant)
        } catch (e: Exception) {
            false
        }
        // some phones refuse the constant; a short pulse of the right weight keeps the feel
        if (!done) pulse(tier)
    }

    @SuppressLint("MissingPermission")
    private fun pulse(tier: HapticTier) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val (millis, amplitude) = when (tier) {
            HapticTier.TAP -> 8L to 60
            HapticTier.KEY -> 14L to 130
            HapticTier.HEAVY -> 24L to 240
        }
        try {
            val a = if (v.hasAmplitudeControl()) amplitude else VibrationEffect.DEFAULT_AMPLITUDE
            v.vibrate(VibrationEffect.createOneShot(millis, a))
        } catch (e: Exception) {
            // Vibration is a nicety; never let it crash the calculator.
        }
    }
}

@Composable
fun rememberFeedback(): Feedback {
    val context = LocalContext.current
    val view = LocalView.current
    return remember(context, view) { Feedback(context.applicationContext, view) }
}
