package com.tapecalc.feature.tools

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Functions
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.tapecalc.core.math.AngleMode
import com.tapecalc.core.math.EvalContext
import com.tapecalc.core.math.graph.GraphCompile
import com.tapecalc.core.math.graph.GraphFunction
import com.tapecalc.core.math.graph.Graphing
import com.tapecalc.core.precision.NumberFormatter
import java.math.BigDecimal
import kotlin.math.abs
import kotlin.math.ceil
import kotlinx.coroutines.delay

private fun shortNumber(v: Double): String {
    if (v.isNaN() || v.isInfinite()) return "undefined"
    val bd = try { BigDecimal(v) } catch (e: NumberFormatException) { return "undefined" }
    return NumberFormatter.format(bd.round(java.math.MathContext(8)))
}

/** The view window: which part of the plane is on screen. */
private class Window(val xMin: Double, val xMax: Double, val yMin: Double, val yMax: Double) {
    val xSpan: Double get() = xMax - xMin
    val ySpan: Double get() = yMax - yMin

    /** Zoom about the middle of the window: [factor] below 1 zooms in, above 1 zooms out. */
    fun zoomed(factor: Double): Window {
        val cx = (xMin + xMax) / 2
        val cy = (yMin + yMax) / 2
        val hx = xSpan / 2 * factor
        val hy = ySpan / 2 * factor
        return Window(cx - hx, cx + hx, cy - hy, cy + hy)
    }

    /** Same zoom, moved so that (0, 0) is in the middle. */
    fun centeredOnOrigin(): Window = Window(-xSpan / 2, xSpan / 2, -ySpan / 2, ySpan / 2)

    fun isUsable(): Boolean = xSpan in 1e-9..1e9 && ySpan in 1e-9..1e9
}

/** The starting view: x from -10 to 10, with the same scale on y so circles look round. */
private fun homeWindow(size: IntSize): Window {
    val ratio = if (size.width > 0) size.height.toDouble() / size.width else 0.6
    val yr = 10.0 * ratio.coerceIn(0.2, 5.0)
    return Window(-10.0, 10.0, -yr, yr)
}

/**
 * Full-screen graph of up to three functions of x.
 *
 * Drag to move, pinch to zoom, tap to read a point. The buttons on the right zoom in and out, go back to the
 * starting view, or put (0, 0) in the middle. The equation boxes are tucked behind the f(x) button so the keyboard
 * only ever appears when one of them is tapped.
 */
@Composable
internal fun GraphPane(angleMode: AngleMode, onBack: () -> Unit, modifier: Modifier = Modifier) {
    var f1 by rememberSaveable { mutableStateOf("sin(x)") }
    var f2 by rememberSaveable { mutableStateOf("") }
    var f3 by rememberSaveable { mutableStateOf("") }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var window by remember { mutableStateOf(Window(-10.0, 10.0, -6.0, 6.0)) }
    var placed by remember { mutableStateOf(false) }
    var traceX by remember { mutableStateOf<Double?>(null) }
    var points by remember { mutableStateOf<List<String>>(emptyList()) }
    var showEquations by remember { mutableStateOf(false) }
    // While a finger is moving the view, curves are drawn with fewer samples so dragging stays smooth.
    var interacting by remember { mutableStateOf(false) }

    LaunchedEffect(window) {
        if (interacting) {
            delay(160)
            interacting = false
        }
    }

    val ctx = remember(angleMode) { EvalContext(angleMode = angleMode) }
    val compiled: List<Pair<String, GraphCompile?>> = remember(f1, f2, f3, ctx) {
        listOf(f1, f2, f3).map { t -> t to (if (t.isBlank()) null else Graphing.compile(t, ctx)) }
    }
    val functions: List<GraphFunction> = compiled.mapNotNull { (it.second as? GraphCompile.Ok)?.function }

    val colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.error)
    val axisColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val paint = remember { android.graphics.Paint().apply { isAntiAlias = true } }

    Box(modifier = modifier.background(MaterialTheme.colorScheme.background)) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .onSizeChanged {
                    canvasSize = it
                    // the first time the size is known, start with square units
                    if (!placed && it.width > 0) {
                        window = homeWindow(it)
                        placed = true
                    }
                }
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val w = window
                        val width = size.width.toFloat().coerceAtLeast(1f)
                        val height = size.height.toFloat().coerceAtLeast(1f)
                        val z = zoom.toDouble().coerceIn(0.5, 2.0)
                        // keep the point under the fingers fixed while zooming
                        val cx = w.xMin + w.xSpan * centroid.x / width
                        val cy = w.yMax - w.ySpan * centroid.y / height
                        var nxMin = cx - (cx - w.xMin) / z
                        var nxMax = cx + (w.xMax - cx) / z
                        var nyMin = cy - (cy - w.yMin) / z
                        var nyMax = cy + (w.yMax - cy) / z
                        val dx = -pan.x / width * (nxMax - nxMin)
                        val dy = pan.y / height * (nyMax - nyMin)
                        nxMin += dx; nxMax += dx; nyMin += dy; nyMax += dy
                        val next = Window(nxMin, nxMax, nyMin, nyMax)
                        if (next.isUsable()) {
                            interacting = true
                            window = next
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { tap ->
                        val w = window
                        traceX = w.xMin + w.xSpan * tap.x / size.width.toFloat().coerceAtLeast(1f)
                    }
                }
        ) {
            drawGraph(window, functions, colors, axisColor, gridColor, labelColor, traceX, paint, interacting)
        }

        // ---- top left: back and the equations -------------------------------------------------------
        Column(
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp).widthIn(max = 420.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), tonalElevation = 3.dp) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back to Tools")
                    }
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (showEquations) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    contentColor = if (showEquations) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    tonalElevation = 3.dp,
                    onClick = { showEquations = !showEquations }
                ) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Functions, contentDescription = null)
                        Text(
                            if (showEquations) "  Hide equations" else "  Equations",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
                // small colour keys for the curves that are drawn
                functions.forEachIndexed { i, f ->
                    Text(
                        "y${i + 1}",
                        color = colors[i % colors.size],
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            if (showEquations) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    tonalElevation = 4.dp
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            Triple("y₁ =", f1, { s: String -> f1 = s.take(120) }),
                            Triple("y₂ =", f2, { s: String -> f2 = s.take(120) }),
                            Triple("y₃ =", f3, { s: String -> f3 = s.take(120) })
                        ).forEachIndexed { i, (label, value, set) ->
                            val err = (compiled[i].second as? GraphCompile.Err)?.error?.message
                            OutlinedTextField(
                                value = value,
                                onValueChange = { set(it); traceX = null; points = emptyList() },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(label) },
                                singleLine = true,
                                isError = err != null,
                                supportingText = { if (err != null) Text(err) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                val first = functions.firstOrNull()
                                if (first != null) {
                                    val (lo, hi) = Graphing.autoYRange(first, window.xMin, window.xMax)
                                    if (hi > lo) window = Window(window.xMin, window.xMax, lo, hi)
                                }
                            }) { Text("Fit height") }
                            OutlinedButton(onClick = {
                                val out = ArrayList<String>()
                                functions.forEachIndexed { i, f ->
                                    Graphing.roots(f, window.xMin, window.xMax).take(8).forEach { out.add("y${i + 1} zero at x = ${shortNumber(it)}") }
                                    Graphing.extremes(f, window.xMin, window.xMax).take(8).forEach {
                                        out.add("y${i + 1} ${if (it.isMax) "peak" else "valley"} at (${shortNumber(it.x)}, ${shortNumber(it.y)})")
                                    }
                                }
                                for (a in functions.indices) for (b in a + 1 until functions.size) {
                                    Graphing.intersections(functions[a], functions[b], window.xMin, window.xMax).take(8).forEach {
                                        out.add("y${a + 1} meets y${b + 1} at (${shortNumber(it.first)}, ${shortNumber(it.second)})")
                                    }
                                }
                                points = if (out.isEmpty()) listOf("No special points in this view.") else out
                                showEquations = false
                            }) { Text("Special points") }
                        }
                    }
                }
            }
        }

        // ---- right side: zoom, home, centre ---------------------------------------------------------
        Column(
            modifier = Modifier.align(Alignment.CenterEnd).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val container = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
            val content = MaterialTheme.colorScheme.onSurfaceVariant
            SmallFloatingActionButton(
                onClick = { window = window.zoomed(0.6).takeIf { it.isUsable() } ?: window },
                containerColor = container, contentColor = content
            ) { Icon(Icons.Outlined.Add, contentDescription = "Zoom in") }
            SmallFloatingActionButton(
                onClick = { window = window.zoomed(1.0 / 0.6).takeIf { it.isUsable() } ?: window },
                containerColor = container, contentColor = content
            ) { Icon(Icons.Outlined.Remove, contentDescription = "Zoom out") }
            SmallFloatingActionButton(
                onClick = { window = homeWindow(canvasSize); traceX = null },
                containerColor = container, contentColor = content
            ) { Icon(Icons.Outlined.Home, contentDescription = "Reset the view") }
            SmallFloatingActionButton(
                onClick = { window = window.centeredOnOrigin() },
                containerColor = container, contentColor = content
            ) { Icon(Icons.Outlined.CenterFocusStrong, contentDescription = "Centre on 0, 0") }
        }

        // ---- bottom: the point that was tapped, or the special points ---------------------------------
        val tx = traceX
        Surface(
            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp).fillMaxWidth(0.82f).heightIn(max = 190.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            tonalElevation = 3.dp
        ) {
            Column(Modifier.padding(12.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (tx != null && functions.isNotEmpty()) {
                    functions.forEachIndexed { i, f ->
                        Text(
                            "y${i + 1}(${shortNumber(tx)}) = ${shortNumber(f.at(tx))}   slope ${shortNumber(Graphing.slopeAt(f, tx))}",
                            fontFamily = FontFamily.Monospace,
                            color = colors[i % colors.size],
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else if (points.isNotEmpty()) {
                    points.forEach { Text(it, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
                } else {
                    Text(
                        "Drag to move, pinch to zoom, tap to read a point.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawGraph(
    w: Window,
    functions: List<GraphFunction>,
    colors: List<Color>,
    axisColor: Color,
    gridColor: Color,
    labelColor: Int,
    traceX: Double?,
    paint: android.graphics.Paint,
    interacting: Boolean
) {
    val width = size.width
    val height = size.height
    if (width <= 0f || height <= 0f) return
    val xSpan = w.xSpan
    val ySpan = w.ySpan
    fun px(x: Double): Float = ((x - w.xMin) / xSpan * width).toFloat()
    fun py(y: Double): Float = ((w.yMax - y) / ySpan * height).toFloat()

    paint.color = labelColor
    paint.textSize = 11.dp.toPx()
    val native = drawContext.canvas.nativeCanvas

    // grid and tick labels
    val xStep = Graphing.tickStep(xSpan)
    val yStep = Graphing.tickStep(ySpan)
    val axisY = py(0.0).coerceIn(16.dp.toPx(), height - 6.dp.toPx())
    val axisX = px(0.0).coerceIn(4.dp.toPx(), width - 44.dp.toPx())
    var gx = ceil(w.xMin / xStep) * xStep
    var guard = 0
    while (gx <= w.xMax && guard++ < 60) {
        val x = px(gx)
        drawLine(gridColor, Offset(x, 0f), Offset(x, height), strokeWidth = 1f)
        if (abs(gx) > xStep / 2) native.drawText(shortNumber(gx), x + 3f, axisY - 3f, paint)
        gx += xStep
    }
    var gy = ceil(w.yMin / yStep) * yStep
    guard = 0
    while (gy <= w.yMax && guard++ < 60) {
        val y = py(gy)
        drawLine(gridColor, Offset(0f, y), Offset(width, y), strokeWidth = 1f)
        if (abs(gy) > yStep / 2) native.drawText(shortNumber(gy), axisX + 4f, y - 3f, paint)
        gy += yStep
    }
    // axes, with their names
    if (w.yMin <= 0.0 && w.yMax >= 0.0) {
        drawLine(axisColor, Offset(0f, py(0.0)), Offset(width, py(0.0)), strokeWidth = 2.5f)
        paint.textSize = 14.dp.toPx()
        native.drawText("x", width - 16.dp.toPx(), py(0.0) - 6.dp.toPx(), paint)
        paint.textSize = 11.dp.toPx()
    }
    if (w.xMin <= 0.0 && w.xMax >= 0.0) {
        drawLine(axisColor, Offset(px(0.0), 0f), Offset(px(0.0), height), strokeWidth = 2.5f)
        paint.textSize = 14.dp.toPx()
        native.drawText("y", px(0.0) + 8.dp.toPx(), 20.dp.toPx(), paint)
        paint.textSize = 11.dp.toPx()
    }
    if (w.xMin <= 0.0 && w.xMax >= 0.0 && w.yMin <= 0.0 && w.yMax >= 0.0) {
        native.drawText("0", px(0.0) + 4.dp.toPx(), py(0.0) + 14.dp.toPx(), paint)
    }

    // curves
    val samples = if (interacting) 220 else (width / 2f).toInt().coerceIn(120, 700)
    functions.forEachIndexed { i, f ->
        val color = colors[i % colors.size]
        for (seg in Graphing.segments(f, w.xMin, w.xMax, w.yMin, w.yMax, samples)) {
            val path = Path()
            for (k in 0 until seg.size) {
                val x = px(seg.xs[k])
                val y = py(seg.ys[k]).coerceIn(-100000f, 100000f)
                if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color, style = Stroke(width = 3.5f, cap = StrokeCap.Round))
        }
    }

    // trace marker
    if (traceX != null) {
        val x = px(traceX)
        drawLine(axisColor, Offset(x, 0f), Offset(x, height), strokeWidth = 1.5f)
        functions.forEachIndexed { i, f ->
            val y = f.at(traceX)
            if (!y.isNaN()) drawCircle(colors[i % colors.size], radius = 7f, center = Offset(x, py(y).coerceIn(-100000f, 100000f)))
        }
    }
}
