package com.tapecalc.pro

import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tapecalc.core.data.settings.AppSettings
import com.tapecalc.core.ui.ButtonShape
import com.tapecalc.core.ui.KeypadStyle
import com.tapecalc.core.ui.ThemeSettings
import com.tapecalc.core.ui.TapeTheme
import com.tapecalc.core.ui.resolveTheme
import com.tapecalc.pro.presentation.CalculatorViewModel
import com.tapecalc.pro.ui.AppRoot
import dagger.hilt.android.AndroidEntryPoint

private fun AppSettings.toThemeSettings() = ThemeSettings(
    themeId = themeId,
    followSystem = followSystem,
    customAccent = customAccent,
    keypadStyle = KeypadStyle.fromName(keypadStyle),
    buttonShape = ButtonShape.fromName(buttonShape),
    fontScale = fontScale
)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: CalculatorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // The view model is created here, before the first frame. Its settings start from the saved
        // values (read instantly from a tiny local copy), not from the defaults.
        val saved = viewModel.settings.value

        // Paint the window in the saved theme's colour straight away, so nothing of the default
        // (dark) window background shows while the first frame is prepared.
        val systemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        window.setBackgroundDrawable(ColorDrawable(resolveTheme(saved.toThemeSettings(), systemDark).background.toArgb()))

        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            TapeTheme(settings = settings.toThemeSettings()) {
                // Keep the window the same colour as the app so nothing can show through during transitions.
                val background = MaterialTheme.colorScheme.background.toArgb()
                SideEffect { window.setBackgroundDrawable(ColorDrawable(background)) }
                AppRoot(viewModel)
            }
        }
    }
}
