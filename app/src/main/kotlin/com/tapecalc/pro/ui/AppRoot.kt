package com.tapecalc.pro.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tapecalc.core.data.domain.ExportFormat
import com.tapecalc.core.data.settings.AppSettings
import com.tapecalc.core.ui.KeyAction
import com.tapecalc.feature.basic.BasicScreen
import com.tapecalc.feature.basic.DisplayActions
import com.tapecalc.feature.converter.ConverterScreen
import com.tapecalc.feature.history.EntryEditorActions
import com.tapecalc.feature.history.EntryEditorDialog
import com.tapecalc.feature.history.HistoryActions
import com.tapecalc.feature.history.HistoryHub
import com.tapecalc.feature.history.HistorySection
import com.tapecalc.feature.scientific.ScientificScreen
import com.tapecalc.pro.presentation.CalculatorViewModel
import com.tapecalc.pro.presentation.UiEvent
import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Tab(val title: String, val icon: ImageVector) {
    BASIC("Basic", Icons.Outlined.Calculate),
    SCIENTIFIC("Scientific", Icons.Outlined.Science),
    CONVERT("Convert", Icons.Outlined.SwapHoriz),
    TAPE("Tape", Icons.Outlined.History)
}

private enum class SidePane(val title: String) {
    TAPE("Tape"), VARIABLES("Variables"), NOTES("Notes"), CONVERT("Convert"), SETTINGS("Settings")
}

private class PendingExport(val text: String, val format: ExportFormat, val workspaceName: String)

/** Maps a physical keyboard key to a calculator action (Bluetooth keyboards, DeX, tablets, Chromebooks). */
private fun hardwareAction(event: androidx.compose.ui.input.key.KeyEvent): KeyAction? {
    if (event.type != KeyEventType.KeyDown) return null
    when (event.key) {
        Key.Enter, Key.NumPadEnter -> return KeyAction.Equals
        Key.Backspace, Key.Delete -> return KeyAction.Backspace
        Key.Escape -> return KeyAction.AllClear
        else -> Unit
    }
    val c = event.utf16CodePoint
    if (c <= 0 || c > 0xFFFF) return null
    return when (val ch = c.toChar()) {
        in '0'..'9' -> KeyAction.Insert(ch.toString())
        '.', ',' -> KeyAction.Insert(".")
        '+' -> KeyAction.Insert("+")
        '-' -> KeyAction.Insert("−")
        '*' -> KeyAction.Insert("×")
        '/' -> KeyAction.Insert("÷")
        '^' -> KeyAction.Insert("^")
        '(' -> KeyAction.Insert("(")
        ')' -> KeyAction.Insert(")")
        '%' -> KeyAction.Insert("%")
        '!' -> KeyAction.Insert("!")
        '=' -> KeyAction.Equals
        else -> null
    }
}

/** Smooth tab change: one eased slide (a little longer when jumping over tabs), never a snap. */
@OptIn(ExperimentalFoundationApi::class)
private suspend fun PagerState.goToTab(target: Int) {
    if (target == currentPage) return
    val duration = if (abs(target - currentPage) > 1) 340 else 280
    animateScrollToPage(target, animationSpec = tween(durationMillis = duration, easing = FastOutSlowInEasing))
}

// The screens below collect the fast-changing display state THEMSELVES. That way a key press
// recomposes only the display, not the whole app and every key (this was the main smoothness problem).

@Composable
private fun BasicTab(viewModel: CalculatorViewModel, actions: DisplayActions, leftHanded: Boolean, modifier: Modifier = Modifier) {
    val display by viewModel.display.collectAsStateWithLifecycle()
    val miniTape by viewModel.miniTape.collectAsStateWithLifecycle()
    BasicScreen(display, miniTape, actions, leftHanded, modifier)
}

@Composable
private fun ScientificTab(viewModel: CalculatorViewModel, actions: DisplayActions, leftHanded: Boolean, modifier: Modifier = Modifier) {
    val display by viewModel.display.collectAsStateWithLifecycle()
    val miniTape by viewModel.miniTape.collectAsStateWithLifecycle()
    ScientificScreen(display, miniTape, actions, leftHanded, modifier)
}

@Composable
private fun HistoryTab(
    viewModel: CalculatorViewModel,
    section: HistorySection,
    onSectionChange: ((HistorySection) -> Unit)?,
    groupDigits: Boolean,
    actions: HistoryActions,
    modifier: Modifier = Modifier
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val variables by viewModel.variables.collectAsStateWithLifecycle()
    val workspace by viewModel.activeWorkspace.collectAsStateWithLifecycle()
    HistoryHub(entries, variables, section, onSectionChange, workspace?.name ?: "", groupDigits, actions, modifier)
}

/** The edit-calculation dialog; it observes the tape itself so the app root does not have to. */
@Composable
private fun EditorHost(viewModel: CalculatorViewModel, autoRecalc: Boolean) {
    val editingId by viewModel.editingId.collectAsStateWithLifecycle()
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val editing = editingId?.let { id -> entries.firstOrNull { it.id == id } } ?: return
    EntryEditorDialog(
        entry = editing,
        autoRecalc = autoRecalc,
        preview = { text -> viewModel.previewEdit(editing.id, text) },
        actions = EntryEditorActions(
            onSave = { expression, label, note, tags -> viewModel.saveEdit(editing.id, expression, label, note, tags) },
            onTogglePin = { viewModel.togglePin(editing.id) },
            onDuplicate = { viewModel.duplicate(editing.id) },
            onDelete = { viewModel.deleteEntry(editing.id) },
            onUseResult = { viewModel.useResultOf(editing) },
            onCopyExpression = { viewModel.copyEntryText(editing.expression, "Expression copied") },
            onCopyResult = { viewModel.copyEntryText(editing.resultFull.ifEmpty { editing.resultText }, "Result copied") }
        ),
        onDismiss = viewModel::closeEditor
    )
}

/** Full-screen settings page (phones): back arrow, title, then the settings list. */
@Composable
private fun SettingsPage(
    settings: AppSettings,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    onReplayTutorial: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                }
                Text("Settings", style = MaterialTheme.typography.titleLarge)
            }
            SettingsScreen(
                settings = settings,
                onChange = onChange,
                onReplayTutorial = onReplayTutorial,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppRoot(viewModel: CalculatorViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val workspaces by viewModel.workspaces.collectAsStateWithLifecycle()
    val activeWorkspace by viewModel.activeWorkspace.collectAsStateWithLifecycle()
    val editingId by viewModel.editingId.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val feedback = rememberFeedback()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showTypeDialog by rememberSaveable { mutableStateOf(false) }
    var showWorkspaces by rememberSaveable { mutableStateOf(false) }
    var replayTutorial by rememberSaveable { mutableStateOf(false) }
    var calcMode by rememberSaveable { mutableStateOf(0) } // two-pane: 0 = basic, 1 = scientific
    var sidePane by rememberSaveable { mutableStateOf(SidePane.TAPE) }
    var historySection by rememberSaveable { mutableStateOf(HistorySection.TAPE) }
    var pendingExport by remember { mutableStateOf<PendingExport?>(null) }
    // Kept here (not inside the phone layout) so the selected tab survives opening Settings.
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { Tab.entries.size })
    // Only the first tab is built at start-up (faster launch); the others are prepared right after.
    var preloadPages by remember { mutableStateOf(0) }

    val showOnboarding = !settings.onboardingDone || replayTutorial
    LaunchedEffect(showOnboarding) {
        if (showOnboarding) {
            preloadPages = 0
        } else {
            delay(300)
            preloadPages = Tab.entries.size - 1
        }
    }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val export = pendingExport
        if (uri != null && export != null) {
            val ok = try {
                context.contentResolver.openOutputStream(uri)?.use { it.write(export.text.toByteArray(Charsets.UTF_8)) } != null
            } catch (e: Exception) {
                false
            }
            scope.launch { snackbar.showSnackbar(if (ok) "Saved" else "Could not save the file") }
        }
        pendingExport = null
    }

    // Every event is handled in its own coroutine. Showing a snackbar suspends until it disappears; when
    // that happened inside this collector, later events (such as paste) queued up behind it and then all
    // fired at once. A new message now replaces the current one instead of waiting for it.
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is UiEvent.Message -> launch {
                    snackbar.currentSnackbarData?.dismiss()
                    snackbar.showSnackbar(event.text)
                }
                is UiEvent.CopyText -> {
                    clipboard.setText(AnnotatedString(event.text))
                    launch {
                        snackbar.currentSnackbarData?.dismiss()
                        snackbar.showSnackbar(event.message)
                    }
                }
                is UiEvent.ExportReady -> pendingExport = PendingExport(event.text, event.format, event.workspaceName)
            }
        }
    }

    // The handlers below are remembered so their identity never changes between recompositions;
    // that lets Compose skip the keypads while the display text changes.
    val latestSettings by rememberUpdatedState(settings)
    val onKey: (KeyAction) -> Unit = remember(viewModel, feedback, clipboard) {
        { action ->
            feedback.key(latestSettings.hapticLevel, latestSettings.soundEnabled)
            if (action == KeyAction.Paste) viewModel.onPaste(clipboard.getText()?.text) else viewModel.onKey(action)
        }
    }

    val displayActions = remember(viewModel, onKey, clipboard) {
        DisplayActions(
            onWorkspaceClick = { showWorkspaces = true },
            onTypeExpression = { showTypeDialog = true },
            onCopyResult = viewModel::copyResult,
            onCopyExpression = viewModel::copyExpression,
            onPaste = { viewModel.onPaste(clipboard.getText()?.text) },
            onOpenTape = { sidePane = SidePane.TAPE },
            onSettings = { sidePane = SidePane.SETTINGS },
            onAction = onKey
        )
    }
    val phoneActions = remember(displayActions, pagerState) {
        DisplayActions(
            onWorkspaceClick = displayActions.onWorkspaceClick,
            onTypeExpression = displayActions.onTypeExpression,
            onCopyResult = displayActions.onCopyResult,
            onCopyExpression = displayActions.onCopyExpression,
            onPaste = displayActions.onPaste,
            onOpenTape = { scope.launch { pagerState.goToTab(Tab.TAPE.ordinal) } },
            onSettings = { showSettings = true },
            onAction = displayActions.onAction
        )
    }

    val historyActions = remember(viewModel) {
        HistoryActions(
            onEntryClick = viewModel::openEditor,
            onExport = viewModel::export,
            onRecalculateStale = viewModel::recalculateStale,
            onClearTape = viewModel::clearTape,
            onAddVariable = { text -> viewModel.addVariable(text) },
            onDeleteVariable = viewModel::deleteVariable,
            onInsertText = { text -> viewModel.insertText(text) }
        )
    }
    val onChangeSettings: ((AppSettings) -> AppSettings) -> Unit = remember(viewModel) { { transform -> viewModel.updateSettings(transform) } }
    val onReplayTutorial: () -> Unit = remember { { replayTutorial = true } }
    val useInCalculator: (String) -> Unit = remember(viewModel, pagerState) {
        { text ->
            viewModel.submitText(text)
            scope.launch { pagerState.goToTab(Tab.BASIC.ordinal) }
        }
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(showOnboarding) {
        if (showOnboarding) return@LaunchedEffect
        try {
            focusRequester.requestFocus()
        } catch (e: Exception) {
            // Focus is only a convenience for hardware keyboards.
        }
    }

    Crossfade(targetState = showOnboarding, animationSpec = tween(250), label = "root") { onboarding ->
        if (onboarding) {
            OnboardingScreen(onFinish = {
                replayTutorial = false
                viewModel.completeOnboarding()
            })
        } else {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .focusRequester(focusRequester)
                    .focusable()
                    .onKeyEvent { event ->
                        val action = hardwareAction(event)
                        if (action != null && !showSettings) {
                            onKey(action)
                            true
                        } else false
                    }
            ) {
                val twoPane = maxWidth >= 840.dp

                if (twoPane) {
                    Scaffold(
                        containerColor = MaterialTheme.colorScheme.background,
                        snackbarHost = { SnackbarHost(snackbar) }
                    ) { padding ->
                        val calcPane: @Composable (Modifier) -> Unit = { m ->
                            Column(m) {
                                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                                    FilterChip(selected = calcMode == 0, onClick = { calcMode = 0 }, label = { Text("Basic") })
                                    FilterChip(
                                        selected = calcMode == 1, onClick = { calcMode = 1 }, label = { Text("Scientific") },
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                                if (calcMode == 0) BasicTab(viewModel, displayActions, settings.leftHanded, Modifier.weight(1f))
                                else ScientificTab(viewModel, displayActions, settings.leftHanded, Modifier.weight(1f))
                            }
                        }
                        val sidePaneContent: @Composable (Modifier) -> Unit = { m ->
                            Column(m) {
                                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                                    SidePane.entries.forEachIndexed { i, p ->
                                        FilterChip(
                                            selected = sidePane == p,
                                            onClick = { sidePane = p },
                                            label = { Text(p.title) },
                                            modifier = Modifier.padding(start = if (i == 0) 0.dp else 6.dp)
                                        )
                                    }
                                }
                                when (sidePane) {
                                    SidePane.TAPE -> HistoryTab(
                                        viewModel, HistorySection.TAPE, null, settings.groupDigits, historyActions, Modifier.weight(1f)
                                    )
                                    SidePane.VARIABLES -> HistoryTab(
                                        viewModel, HistorySection.VARIABLES, null, settings.groupDigits, historyActions, Modifier.weight(1f)
                                    )
                                    SidePane.NOTES -> HistoryTab(
                                        viewModel, HistorySection.NOTES, null, settings.groupDigits, historyActions, Modifier.weight(1f)
                                    )
                                    SidePane.CONVERT -> ConverterScreen(
                                        onUseInCalculator = { viewModel.submitText(it) },
                                        modifier = Modifier.weight(1f)
                                    )
                                    SidePane.SETTINGS -> SettingsScreen(
                                        settings = settings,
                                        onChange = onChangeSettings,
                                        onReplayTutorial = onReplayTutorial,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                        Row(Modifier.fillMaxSize().padding(padding)) {
                            if (settings.leftHanded) {
                                sidePaneContent(Modifier.weight(1f).fillMaxHeight())
                                VerticalDivider()
                                calcPane(Modifier.weight(1f).fillMaxHeight())
                            } else {
                                calcPane(Modifier.weight(1f).fillMaxHeight())
                                VerticalDivider()
                                sidePaneContent(Modifier.weight(1f).fillMaxHeight())
                            }
                        }
                    }
                } else {
                    Box(Modifier.fillMaxSize()) {
                        Scaffold(
                            containerColor = MaterialTheme.colorScheme.background,
                            snackbarHost = { SnackbarHost(snackbar) },
                            bottomBar = {
                                NavigationBar {
                                    Tab.entries.forEach { tab ->
                                        NavigationBarItem(
                                            selected = pagerState.currentPage == tab.ordinal,
                                            onClick = { scope.launch { pagerState.goToTab(tab.ordinal) } },
                                            icon = { Icon(tab.icon, contentDescription = null) },
                                            label = { Text(tab.title) }
                                        )
                                    }
                                }
                            }
                        ) { padding ->
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize().padding(padding),
                                beyondViewportPageCount = preloadPages
                            ) { page ->
                                // Pages fade and shrink slightly while they slide, which makes tab changes look smooth.
                                Box(
                                    Modifier.fillMaxSize().graphicsLayer {
                                        val distance = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                                            .absoluteValue.coerceIn(0f, 1f)
                                        alpha = 1f - 0.6f * distance
                                        val scale = 1f - 0.05f * distance
                                        scaleX = scale
                                        scaleY = scale
                                    }
                                ) {
                                    when (Tab.entries[page]) {
                                        Tab.BASIC -> BasicTab(viewModel, phoneActions, settings.leftHanded)
                                        Tab.SCIENTIFIC -> ScientificTab(viewModel, phoneActions, settings.leftHanded)
                                        Tab.CONVERT -> ConverterScreen(onUseInCalculator = useInCalculator)
                                        Tab.TAPE -> HistoryTab(
                                            viewModel, historySection, { historySection = it }, settings.groupDigits, historyActions
                                        )
                                    }
                                }
                            }
                        }

                        // Settings slides over the calculator instead of replacing it, so the tabs stay alive underneath.
                        AnimatedVisibility(
                            visible = showSettings,
                            enter = slideInHorizontally(tween(240)) { it / 3 } + fadeIn(tween(240)),
                            exit = slideOutHorizontally(tween(200)) { it / 3 } + fadeOut(tween(200))
                        ) {
                            SettingsPage(
                                settings = settings,
                                onChange = onChangeSettings,
                                onReplayTutorial = onReplayTutorial,
                                onBack = { showSettings = false }
                            )
                        }
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- dialogs (shared by both layouts)

    if (showTypeDialog) {
        TypeExpressionDialog(
            initial = viewModel.display.value.expression,
            onCalculate = {
                showTypeDialog = false
                viewModel.submitText(it)
            },
            onDismiss = { showTypeDialog = false }
        )
    }

    if (showWorkspaces) {
        WorkspaceDialog(
            workspaces = workspaces,
            activeId = activeWorkspace?.id ?: 0L,
            onSelect = viewModel::selectWorkspace,
            onAdd = viewModel::addWorkspace,
            onRename = viewModel::renameWorkspace,
            onDelete = viewModel::deleteWorkspace,
            onDismiss = { showWorkspaces = false }
        )
    }

    EditorHost(viewModel, settings.autoRecalc)

    val export = pendingExport
    if (export != null && editingId == null) {
        ExportDialog(
            workspaceName = export.workspaceName,
            formatLabel = export.format.label,
            onShare = {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "CruxCalculator - ${export.workspaceName}")
                    putExtra(Intent.EXTRA_TEXT, export.text)
                }
                try {
                    context.startActivity(Intent.createChooser(send, "Share ${export.workspaceName}"))
                } catch (e: Exception) {
                    scope.launch { snackbar.showSnackbar("No app available to share with") }
                }
                pendingExport = null
            },
            onSaveFile = {
                saveLauncher.launch("tape-${export.workspaceName.lowercase().replace(Regex("[^a-z0-9]+"), "-")}.${export.format.extension}")
            },
            onDismiss = { pendingExport = null }
        )
    }
}
