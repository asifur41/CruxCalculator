package com.tapecalc.pro

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.tapecalc.core.ui.KeyAction
import com.tapecalc.core.ui.TapeTheme
import com.tapecalc.core.ui.ThemeSettings
import com.tapecalc.feature.basic.BasicKeypad
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI test (instrumented). Run on a device/emulator with: ./gradlew connectedDebugAndroidTest
 * It is intentionally not part of the GitHub Actions workflow, which runs only JVM unit tests.
 */
class KeypadUiTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun tappingKeysReportsTheMatchingActions() {
        val received = mutableListOf<KeyAction>()
        compose.setContent {
            TapeTheme(settings = ThemeSettings()) {
                BasicKeypad(onAction = { received += it }, modifier = Modifier.fillMaxSize())
            }
        }
        compose.onNodeWithContentDescription("7").performClick()
        compose.onNodeWithContentDescription("Add").performClick()
        compose.onNodeWithContentDescription("2").performClick()
        compose.onNodeWithContentDescription("Equals").performClick()

        assertEquals(
            listOf(KeyAction.Insert("7"), KeyAction.Insert("+"), KeyAction.Insert("2"), KeyAction.Equals),
            received
        )
    }

    @Test
    fun longPressGivesTheSecondaryFunction() {
        val received = mutableListOf<KeyAction>()
        compose.setContent {
            TapeTheme(settings = ThemeSettings()) {
                BasicKeypad(onAction = { received += it }, modifier = Modifier.fillMaxSize())
            }
        }
        compose.onNodeWithContentDescription("Backspace").performTouchInput { longClick() }
        assertEquals(listOf<KeyAction>(KeyAction.ClearEntry), received)
    }

    @Test
    fun everyKeyHasAnAccessibleName() {
        compose.setContent {
            TapeTheme(settings = ThemeSettings()) {
                BasicKeypad(onAction = {}, modifier = Modifier.fillMaxSize())
            }
        }
        listOf("All clear", "Backspace", "Percent", "Divide", "Multiply", "Subtract", "Add", "Equals", "Decimal point", "Previous answer")
            .forEach { compose.onNodeWithContentDescription(it).assertExists() }
    }
}
