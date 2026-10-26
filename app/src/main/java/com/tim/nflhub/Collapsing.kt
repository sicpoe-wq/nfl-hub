package com.tim.nflhub

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt

/** True on a sideways phone (or any short window): banners switch to their short, single-row layout. */
@Composable
fun isShortScreen(): Boolean {
    val c = LocalConfiguration.current
    return c.screenHeightDp < 480 || c.screenWidthDp > c.screenHeightDp
}

/**
 * Collapse offset for a banner that scrolls away with the content below it ("exit until collapsed"): scrolling the
 * content up first shrinks the banner down to its compact bar; scrolling back down only expands it once the content
 * has reached its top.
 */
@Stable
class CollapseState(initialFraction: Float = 0f) {
    var range by mutableFloatStateOf(0f)
        internal set
    private var offset by mutableFloatStateOf(0f)
    private var pendingFraction: Float? = initialFraction.takeIf { it > 0f }

    val fraction: Float get() = if (range <= 0f) 0f else (-offset / range).coerceIn(0f, 1f)

    internal fun updateRange(r: Float) {
        if (r == range) return
        val f = pendingFraction ?: fraction
        range = r
        offset = -f * r
        if (r > 0f) pendingFraction = null
    }

    fun expand() { offset = 0f }

    private fun consume(dy: Float): Float {
        val n = (offset + dy).coerceIn(-range, 0f)
        val used = n - offset
        offset = n
        return used
    }

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
            if (available.y < 0f && range > 0f) Offset(0f, consume(available.y)) else Offset.Zero

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
            if (available.y > 0f && range > 0f) Offset(0f, consume(available.y)) else Offset.Zero
    }
}

/**
 * Header whose height goes from the [expanded] content's natural height down to the [compact] bar's natural height
 * as [state] collapses. [background] fills whatever height the header currently has. The expanded content fades out
 * over the first half of the collapse and the compact bar fades in over the second half; only one of them is placed
 * (and tappable) at a time.
 */
@Composable
fun CollapsingHeader(
    state: CollapseState,
    modifier: Modifier = Modifier,
    background: @Composable () -> Unit,
    expanded: @Composable () -> Unit,
    compact: @Composable () -> Unit,
) {
    Layout(
        contents = listOf(
            background,
            { Box(Modifier.graphicsLayer { alpha = (1f - state.fraction * 2f).coerceIn(0f, 1f) }) { expanded() } },
            { Box(Modifier.graphicsLayer { alpha = ((state.fraction - 0.5f) * 2f).coerceIn(0f, 1f) }) { compact() } },
        ),
        modifier = modifier.fillMaxWidth().clipToBounds(),
    ) { (bgM, expM, cmpM), c ->
        val loose = Constraints(minWidth = c.maxWidth, maxWidth = c.maxWidth)
        val e = expM.map { it.measure(loose) }
        val k = cmpM.map { it.measure(loose) }
        val eh = e.maxOfOrNull { it.height } ?: 0
        val kh = k.maxOfOrNull { it.height } ?: 0
        state.updateRange((eh - kh).coerceAtLeast(0).toFloat())
        val f = state.fraction
        val h = (eh - (eh - kh).coerceAtLeast(0) * f).roundToInt()
        val bg = bgM.map { it.measure(Constraints.fixed(c.maxWidth, h)) }
        layout(c.maxWidth, h) {
            bg.forEach { it.place(0, 0) }
            if (f < 0.5f) e.forEach { it.place(0, 0) }
            else k.forEach { it.place(0, h - it.height) }
        }
    }
}

/** Banner + pinned tab row + content; content scrolling drives the banner collapse. Content respects the side
 *  (landscape nav bar / display cutout) and bottom insets. */
@Composable
fun CollapsingScreen(
    state: CollapseState,
    header: @Composable () -> Unit,
    tabs: @Composable () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            header()
            tabs()
            Box(Modifier.weight(1f).fillMaxWidth().nestedScroll(state.connection)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)),
                content = content)
        }
    }
}
