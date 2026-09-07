package com.krrishkumar.focustimer.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput

private const val DOUBLE_TAP_WINDOW_MS = 320L

/**
 * Watches for two quick taps anywhere in this subtree *without consuming* the events.
 *
 * A plain `detectTapGestures` on an ancestor consumes the pointer input, which cancels the
 * click of any button or tappable text underneath it. Observing in the Final pass lets the
 * children handle their own taps normally while we still see the double tap.
 */
fun Modifier.observeDoubleTap(enabled: Boolean, onDoubleTap: () -> Unit): Modifier {
    if (!enabled) return this
    return this.pointerInput(Unit) {
        awaitPointerEventScope {
            var lastTapUpMillis = 0L
            while (true) {
                val down = awaitPointerEvent(PointerEventPass.Final)
                if (down.changes.none { it.changedToDownIgnoreConsumed() }) continue

                var event = down
                while (event.changes.any { it.pressed }) {
                    event = awaitPointerEvent(PointerEventPass.Final)
                }

                val now = System.currentTimeMillis()
                if (now - lastTapUpMillis <= DOUBLE_TAP_WINDOW_MS) {
                    lastTapUpMillis = 0L
                    onDoubleTap()
                } else {
                    lastTapUpMillis = now
                }
            }
        }
    }
}
