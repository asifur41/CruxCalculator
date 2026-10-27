package com.tapecalc.pro.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.SoundEffectConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

/**
 * Key feedback: haptics with a user-controlled strength (0 = off .. 3 = strong) and an optional click sound.
 * Uses only the system vibrator and the system's own touch sounds; nothing is recorded or sent anywhere.
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

    @SuppressLint("MissingPermission")
    fun key(hapticLevel: Int, sound: Boolean) {
        if (sound) view.playSoundEffect(SoundEffectConstants.CLICK)
        if (hapticLevel <= 0) return
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val (millis, amplitude) = when (hapticLevel) {
            1 -> 8L to 40
            2 -> 12L to 110
            else -> 20L to 230
        }
        try {
            if (v.hasAmplitudeControl()) {
                v.vibrate(VibrationEffect.createOneShot(millis, amplitude))
            } else {
                v.vibrate(VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE))
            }
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
