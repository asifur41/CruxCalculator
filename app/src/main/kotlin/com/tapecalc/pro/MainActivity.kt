package com.tapecalc.pro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tapecalc.core.ui.ButtonShape
import com.tapecalc.core.ui.KeypadStyle
import com.tapecalc.core.ui.TapeTheme
import com.tapecalc.core.ui.ThemeSettings
import com.tapecalc.pro.presentation.CalculatorViewModel
import com.tapecalc.pro.ui.AppRoot
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: CalculatorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val themeSettings = ThemeSettings(
                themeId = settings.themeId,
                followSystem = settings.followSystem,
                customAccent = settings.customAccent,
                keypadStyle = KeypadStyle.fromName(settings.keypadStyle),
                buttonShape = ButtonShape.fromName(settings.buttonShape),
                fontScale = settings.fontScale
            )
            TapeTheme(settings = themeSettings) {
                AppRoot(viewModel)
            }
        }
    }
}
