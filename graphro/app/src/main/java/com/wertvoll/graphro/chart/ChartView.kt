package com.wertvoll.graphro.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.graphro.core.chart.ChartData
import com.graphro.core.chart.ChartSpec

/**
 * Draws a chart with the same renderer used for exported images. [onTapCategory] receives the
 * index into [data] categories, or -1 when the tap missed.
 */
@Composable
fun ChartView(
    data: ChartData,
    spec: ChartSpec,
    modifier: Modifier = Modifier,
    description: String = spec.title,
    scale: Float = 1f,
    onTapCategory: ((Int) -> Unit)? = null,
) {
    val ctx = LocalContext.current
    val density = LocalDensity.current.density
    val renderer = remember(density, scale) { ChartRenderer(ChartFonts.get(ctx), density * scale) }
    var m = modifier.semantics { contentDescription = description }
    if (onTapCategory != null) {
        m = m.pointerInput(data, spec) {
            detectTapGestures { pos ->
                val l = renderer.layout(data, spec, size.width.toFloat(), size.height.toFloat())
                onTapCategory(renderer.hitTest(l, data, spec, pos.x, pos.y))
            }
        }
    }
    Canvas(m) {
        drawIntoCanvas { c -> renderer.draw(c.nativeCanvas, data, spec, size.width, size.height) }
    }
}
