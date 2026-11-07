package com.tapecalc.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.reflect.KProperty

/**
 * A tiny key-value store for "where the person was": the open tool, what they typed into its boxes, the graph view and
 * so on. The app backs it with private on-device storage, so it survives leaving the app and closing it. Nothing is sent
 * anywhere.
 */
interface ToolStateStore {
    fun get(key: String): String?

    /** A null [value] removes the key. */
    fun put(key: String, value: String?)
}

/** Keeps values only while the process lives. Used as the default (previews, tests). */
class InMemoryToolStateStore : ToolStateStore {
    private val map = HashMap<String, String>()
    override fun get(key: String): String? = map[key]
    override fun put(key: String, value: String?) {
        if (value == null) map.remove(key) else map[key] = value
    }
}

val LocalToolStateStore = staticCompositionLocalOf<ToolStateStore> { InMemoryToolStateStore() }

/** Observable text that writes itself to the [ToolStateStore] every time it changes. */
class StoredText internal constructor(
    private val key: String,
    private val store: ToolStateStore,
    initial: String
) {
    private var state by mutableStateOf(initial)

    var value: String
        get() = state
        set(v) {
            if (v != state) {
                state = v
                store.put(key, v)
            }
        }
}

/** Like `rememberSaveable`, but the text is also kept after the app is closed. [default] is used the first time only. */
@Composable
fun rememberStoredText(key: String, default: String): StoredText {
    val store = LocalToolStateStore.current
    return remember(key, store) { StoredText(key, store, store.get(key) ?: default) }
}

/** Same as [rememberStoredText] for a whole number (a selected category, a radix...). */
class StoredInt internal constructor(private val text: StoredText, private val fallback: Int) {
    var value: Int
        get() = text.value.toIntOrNull() ?: fallback
        set(v) {
            text.value = v.toString()
        }
}

@Composable
fun rememberStoredInt(key: String, default: Int): StoredInt {
    val text = rememberStoredText(key, default.toString())
    return remember(text) { StoredInt(text, default) }
}

/** Same again for a choice between a few named options (the open side panel, the history section...). */
class StoredEnum<T : Enum<T>> internal constructor(
    private val text: StoredText,
    private val values: Array<T>,
    private val fallback: T
) {
    var value: T
        get() = values.firstOrNull { it.name == text.value } ?: fallback
        set(v) {
            text.value = v.name
        }
}

@Composable
fun <T : Enum<T>> rememberStoredEnum(key: String, values: Array<T>, default: T): StoredEnum<T> {
    val text = rememberStoredText(key, default.name)
    return remember(text) { StoredEnum(text, values, default) }
}

operator fun <T : Enum<T>> StoredEnum<T>.getValue(thisRef: Any?, property: KProperty<*>): T = value

operator fun <T : Enum<T>> StoredEnum<T>.setValue(thisRef: Any?, property: KProperty<*>, newValue: T) {
    value = newValue
}

// `var input by rememberStoredText(...)` reads and writes like an ordinary state variable.
operator fun StoredText.getValue(thisRef: Any?, property: KProperty<*>): String = value

operator fun StoredText.setValue(thisRef: Any?, property: KProperty<*>, newValue: String) {
    value = newValue
}

operator fun StoredInt.getValue(thisRef: Any?, property: KProperty<*>): Int = value

operator fun StoredInt.setValue(thisRef: Any?, property: KProperty<*>, newValue: Int) {
    value = newValue
}
