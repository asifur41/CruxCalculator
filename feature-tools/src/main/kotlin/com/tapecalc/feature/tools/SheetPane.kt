package com.tapecalc.feature.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapecalc.core.math.AngleMode
import com.tapecalc.core.math.EvalContext
import com.tapecalc.core.math.sheet.CellRange
import com.tapecalc.core.math.sheet.CellValue
import com.tapecalc.core.math.sheet.SheetEngine
import com.tapecalc.core.math.sheet.SheetRef
import com.tapecalc.core.math.sheet.SheetState
import com.tapecalc.core.ui.ExpressionField
import com.tapecalc.core.ui.LocalToolStateStore

private const val SHEET_KEY = "tool.sheet.cells"
private val ROW_HEADER_WIDTH = 40.dp
private val CELL_WIDTH = 84.dp
private val CELL_HEIGHT = 44.dp

/**
 * The spreadsheet: a grid of 26 columns (A–Z) by 250 rows. Drag up and down for the rows and left and right for the
 * columns; the column letters and the row numbers stay in view. Tap a cell to select it and type in the box above the grid: a
 * number, a label, or a formula such as `=A1+B1`, `=sum(A1:A9)` or `=mean(B2:B20)`. Switch on "Select range" and tap a
 * second cell to select a block; its count, sum, mean, smallest and largest are shown, and the Σ and x̄ buttons write
 * the formula into the cell below (or beside) the block. The sheet is kept on the device as it is edited.
 */
@Composable
internal fun SheetPane(
    angleMode: AngleMode,
    onUseInCalculator: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val store = LocalToolStateStore.current
    var sheet by remember(store) { mutableStateOf(SheetState.decode(store.get(SHEET_KEY))) }
    val ctx = remember(angleMode) { EvalContext(angleMode = angleMode) }
    val values = remember(sheet, ctx) { SheetEngine.evaluate(sheet, ctx) }

    // the selected block runs from the anchor (the cell being edited) to the focus
    var anchor by rememberSaveable { mutableStateOf(0) }
    var focus by rememberSaveable { mutableStateOf(0) }
    var extend by rememberSaveable { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }

    val anchorCol = SheetRef.colOf(anchor)
    val anchorRow = SheetRef.rowOf(anchor)
    val range = CellRange(anchorCol, anchorRow, SheetRef.colOf(focus), SheetRef.rowOf(focus))

    fun edit(next: SheetState) {
        sheet = next
        store.put(SHEET_KEY, next.encode())
    }

    fun select(col: Int, row: Int) {
        val index = SheetRef.index(col, row)
        notice = null
        if (extend) {
            focus = index
        } else {
            anchor = index
            focus = index
        }
    }

    /** Writes `=function(range)` into the AutoSum cell and selects it. */
    fun autoFormula(function: String) {
        val target = SheetEngine.autoSumTarget(range)
        if (target == null) {
            notice = "There is no free cell below or beside the selection"
            return
        }
        edit(sheet.with(target.first, target.second, SheetEngine.formula(function, range)))
        extend = false
        val index = SheetRef.index(target.first, target.second)
        anchor = index
        focus = index
        notice = "Written to ${SheetRef.name(target.first, target.second)}"
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ExpressionField(
            value = sheet.text(anchorCol, anchorRow),
            onValueChange = { edit(sheet.with(anchorCol, anchorRow, it)) },
            modifier = Modifier.fillMaxWidth(),
            label = "Cell ${SheetRef.name(anchorCol, anchorRow)}",
            placeholder = "12   or   =A1+B1   or   =sum(A1:A9)"
        )

        // what the cell works out to, or why it cannot
        val here = values[anchorCol, anchorRow]
        val shownValue = when (here) {
            is CellValue.Num -> "= ${here.value}"
            is CellValue.Error -> here.message
            is CellValue.Text -> "Text"
            CellValue.Empty -> "Empty"
        }
        Text(
            shownValue,
            style = MaterialTheme.typography.bodySmall,
            color = if (here is CellValue.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = extend,
                onClick = { extend = !extend; notice = null },
                label = { Text("Select range") }
            )
            OutlinedButton(onClick = { autoFormula("sum") }) { Text("Σ sum") }
            OutlinedButton(onClick = { autoFormula("mean") }) { Text("x̄ mean") }
            OutlinedButton(onClick = {
                edit(sheet.withCleared(range))
                notice = null
            }) { Text(if (range.isSingleCell) "Clear cell" else "Clear block") }
            OutlinedButton(onClick = {
                edit(sheet.cleared())
                notice = null
            }) { Text("Clear all") }
        }

        // the numbers of the selected block
        if (!range.isSingleCell) {
            val stats = remember(values, range) { values.stats(range) }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    range.text,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                // a local copy: a property of a class from another module cannot be smart-cast
                val problem = stats.error
                if (problem != null) {
                    Text(problem, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                } else {
                    Text(
                        "count ${stats.count}   sum ${stats.sum}   mean ${stats.mean ?: "-"}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        "min ${stats.min ?: "-"}   max ${stats.max ?: "-"}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { onUseInCalculator(stats.sum.toString()) }) { Text("Use sum") }
                        stats.mean?.let { mean -> TextButton(onClick = { onUseInCalculator(mean.toString()) }) { Text("Use mean") } }
                    }
                }
            }
        }
        notice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }

        // All rows share one horizontal scroll position, so the columns move together; the row numbers (left) and the
        // column letters (top) stay where they are.
        val columnScroll = rememberScrollState()

        // column letters, lined up with the grid below
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.width(ROW_HEADER_WIDTH))
            Row(Modifier.weight(1f).horizontalScroll(columnScroll)) {
                for (c in 0 until SheetRef.COLS) {
                    Text(
                        "${'A' + c}",
                        modifier = Modifier.width(CELL_WIDTH),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (c in range.left..range.right) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            items(count = SheetRef.ROWS, key = { it }) { row ->
                Row(Modifier.fillMaxWidth().height(CELL_HEIGHT)) {
                    Box(Modifier.width(ROW_HEADER_WIDTH).height(CELL_HEIGHT), contentAlignment = Alignment.Center) {
                        Text(
                            "${row + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (row in range.top..range.bottom) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(Modifier.weight(1f).horizontalScroll(columnScroll)) {
                        for (col in 0 until SheetRef.COLS) {
                            SheetCell(
                                value = values[col, row],
                                active = col == anchorCol && row == anchorRow,
                                inRange = range.contains(col, row),
                                onClick = { select(col, row) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetCell(value: CellValue, active: Boolean, inRange: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val text = when (value) {
        is CellValue.Num -> value.value.toString()
        is CellValue.Text -> value.text
        is CellValue.Error -> value.short
        CellValue.Empty -> ""
    }
    val background = when {
        active -> colors.primary.copy(alpha = 0.22f)
        inRange -> colors.primary.copy(alpha = 0.10f)
        else -> Color.Transparent
    }
    val border = if (active) colors.primary else colors.outlineVariant
    Box(
        modifier = Modifier
            .width(CELL_WIDTH)
            .height(CELL_HEIGHT)
            .background(background)
            .border(if (active) 2.dp else 0.5.dp, border)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp),
        contentAlignment = if (value is CellValue.Num) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Text(
            text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 13.sp,
            fontFamily = if (value is CellValue.Num) FontFamily.Monospace else FontFamily.Default,
            color = if (value is CellValue.Error) colors.error else colors.onSurface
        )
    }
}
