package com.tapecalc.feature.tools

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import kotlin.math.floor

private fun shortNumber(v: Double): String {
    if (v.isNaN() || v.isInfinite()) return "undefined"
    val bd = try { BigDecimal(v) } catch (e: NumberFormatException) { return "undefined" }
    return NumberFormatter.format(bd.round(java.math.MathContext(8)))
}

/** The view window: which part of the plane is on screen. */
private class Window(val xMin: Double, val xMax: Double, val yMin: Double, val yMax: Double)

/**
 * Graph screen: up to three functions of x. Drag to pan, pinch to zoom, tap to trace a point.
 * "Fit" picks a sensible vertical range; "Points" lists roots, peaks and where two curves cross.
 */
@Composable
internal fun GraphPane(angleMode: AngleMode, modifier: Modifier = Modifier) {
    var f1 by rememberSaveable { mutableStateOf("sin(x)") }
    var f2 by rememberSaveable { mutableStateOf("") }
    var f3 by rememberSaveable { mutableStateOf("") }
    var window by remember { mutableStateOf(Window(-10.0, 10.0, -6.0, 6.0)) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var traceX by remember { mutableStateOf<Double?>(null) }
    var points by remember { mutableStateOf<List<String>>(emptyList()) }

    val ctx = remember(angleMode) { EvalContext(angleMode = angleMode) }
    val compiled: List<Pair<String, GraphCompile?>> = remember(f1, f2, f3, ctx) {
        listOf(f1, f2, f3).map { t -> t to (if (t.isBlank()) null else Graphing.compile(t, ctx)) }
    }
    val functions: List<GraphFunction> = compiled.mapNotNull { (it.second as? GraphCompile.Ok)?.function }

    val colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.error)
    val axisColor = MaterialTheme.colorScheme.outline
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()

    Column(modifier = modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .clipToBounds()
                .onSizeChanged { canvasSize = it }
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val w = window
                        val width = size.width.toFloat().coerceAtLeast(1f)
                        val height = size.height.toFloat().coerceAtLeast(1f)
                        val z = zoom.toDouble().coerceIn(0.5, 2.0)
                        // keep the point under the fingers fixed while zooming
                        val cx = w.xMin + (w.xMax - w.xMin) * centroid.x / width
                        val cy = w.yMax - (w.yMax - w.yMin) * centroid.y / height
                        var nxMin = cx - (cx - w.xMin) / z
                        var nxMax = cx + (w.xMax - cx) / z
                        var nyMin = cy - (cy - w.yMin) / z
                        var nyMax = cy + (w.yMax - cy) / z
                        val dx = -pan.x / width * (nxMax - nxMin)
                        val dy = pan.y / height * (nyMax - nyMin)
                        nxMin += dx; nxMax += dx; nyMin += dy; nyMax += dy
                        if (nxMax - nxMin in 1e-9..1e9 && nyMax - nyMin in 1e-9..1e9) {
                            window = Window(nxMin, nxMax, nyMin, nyMax)
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { tap ->
                        val w = window
                        traceX = w.xMin + (w.xMax - w.xMin) * tap.x / size.width.toFloat().coerceAtLeast(1f)
                    }
                }
        ) {
            drawGraph(window, functions, colors, axisColor, gridColor, labelColor, traceX)
        }

        val tx = traceX
        if (tx != null && functions.isNotEmpty()) {
            functions.forEachIndexed { i, f ->
                val y = f.at(tx)
                val slope = Graphing.slopeAt(f, tx)
                Text(
                    "y${i + 1}(${shortNumber(tx)}) = ${shortNumber(y)}   slope ${shortNumber(slope)}",
                    fontFamily = FontFamily.Monospace,
                    color = colors[i % colors.size],
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            Text(
                "Drag to move, pinch to zoom, tap to read a point.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { window = Window(-10.0, 10.0, -6.0, 6.0); traceX = null }) { Text("Reset") }
            OutlinedButton(onClick = {
                val first = functions.firstOrNull()
                if (first != null) {
                    val (lo, hi) = Graphing.autoYRange(first, window.xMin, window.xMax)
                    window = Window(window.xMin, window.xMax, lo, hi)
                }
            }) { Text("Fit") }
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
            }) { Text("Points") }
        }
        points.forEach { Text(it, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
    }
}

private fun DrawScope.drawGraph(
    w: Window,
    functions: List<GraphFunction>,
    colors: List<Color>,
    axisColor: Color,
    gridColor: Color,
    labelColor: Int,
    traceX: Double?
) {
    val width = size.width
    val height = size.height
    if (width <= 0f || height <= 0f) return
    val xSpan = w.xMax - w.xMin
    val ySpan = w.yMax - w.yMin
    fun px(x: Double): Float = ((x - w.xMin) / xSpan * width).toFloat()
    fun py(y: Double): Float = ((w.yMax - y) / ySpan * height).toFloat()

    val paint = android.graphics.Paint().apply {
        color = labelColor
        textSize = 11.dp.toPx()
        isAntiAlias = true
    }

    // grid and tick labels
    val xStep = Graphing.tickStep(xSpan)
    val yStep = Graphing.tickStep(ySpan)
    var gx = ceil(w.xMin / xStep) * xStep
    var guard = 0
    while (gx <= w.xMax && guard++ < 60) {
        val x = px(gx)
        drawLine(gridColor, Offset(x, 0f), Offset(x, height), strokeWidth = 1f)
        if (abs(gx) > xStep / 2) drawContext.canvas.nativeCanvas.drawText(shortNumber(gx), x + 3f, (py(0.0)).coerceIn(14f, height - 4f) - 3f, paint)
        gx += xStep
    }
    var gy = ceil(w.yMin / yStep) * yStep
    guard = 0
    while (gy <= w.yMax && guard++ < 60) {
        val y = py(gy)
        drawLine(gridColor, Offset(0f, y), Offset(width, y), strokeWidth = 1f)
        if (abs(gy) > yStep / 2) drawContext.canvas.nativeCanvas.drawText(shortNumber(gy), (px(0.0)).coerceIn(0f, width - 40f) + 4f, y - 3f, paint)
        gy += yStep
    }
    // axes
    if (w.yMin <= 0.0 && w.yMax >= 0.0) drawLine(axisColor, Offset(0f, py(0.0)), Offset(width, py(0.0)), strokeWidth = 2f)
    if (w.xMin <= 0.0 && w.xMax >= 0.0) drawLine(axisColor, Offset(px(0.0), 0f), Offset(px(0.0), height), strokeWidth = 2f)

    // curves
    val samples = (width / 2f).toInt().coerceIn(100, 1200)
    functions.forEachIndexed { i, f ->
        val color = colors[i % colors.size]
        for (seg in Graphing.segments(f, w.xMin, w.xMax, w.yMin, w.yMax, samples)) {
            val path = Path()
            for (k in 0 until seg.size) {
                val x = px(seg.xs[k])
                val y = py(seg.ys[k]).coerceIn(-100000f, 100000f)
                if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color, style = Stroke(width = 3f, cap = StrokeCap.Round))
        }
    }

    // trace marker
    if (traceX != null) {
        val x = px(traceX)
        drawLine(axisColor, Offset(x, 0f), Offset(x, height), strokeWidth = 1.5f)
        functions.forEachIndexed { i, f ->
            val y = f.at(traceX)
            if (!y.isNaN()) drawCircle(colors[i % colors.size], radius = 6f, center = Offset(x, py(y).coerceIn(-100000f, 100000f)))
        }
    }
}
