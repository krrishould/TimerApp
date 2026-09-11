package com.krrishkumar.focustimer.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val DOUBLE_TAP_WINDOW_MS = 320L

/**
 * Watches for single and double taps anywhere in this subtree *without consuming* the events.
 *
 * A plain `detectTapGestures` on an ancestor consumes the pointer input, which cancels the
 * click of any button or tappable text underneath it. Observing in the Final pass lets the
 * children handle their own taps normally while we still see the taps.
 *
 * A single tap is only reported once the double-tap window has passed without a second
 * one, so one gesture never fires both. Drags and long presses aren't taps at all.
 * The callbacks are captured once, so they should read any changing state themselves.
 */
fun Modifier.observeTaps(
    enabled: Boolean,
    onSingleTap: () -> Unit,
    onDoubleTap: () -> Unit
): Modifier {
    if (!enabled) return this
    return this.pointerInput(Unit) {
        coroutineScope {
            var pendingSingleTap: Job? = null
            awaitPointerEventScope {
                while (true) {
                    val down = awaitPointerEvent(PointerEventPass.Final)
                    val first = down.changes.firstOrNull { it.changedToDownIgnoreConsumed() }
                        ?: continue

                    var moved = false
                    var lastUptime = first.uptimeMillis
                    var event = down
                    while (event.changes.any { it.pressed }) {
                        event = awaitPointerEvent(PointerEventPass.Final)
                        event.changes.firstOrNull { it.id == first.id }?.let { change ->
                            lastUptime = change.uptimeMillis
                            val travelled = (change.position - first.position).getDistance()
                            if (travelled > viewConfiguration.touchSlop) moved = true
                        }
                    }

                    val held = lastUptime - first.uptimeMillis
                    if (moved || held > viewConfiguration.longPressTimeoutMillis) continue

                    if (pendingSingleTap?.isActive == true) {
                        pendingSingleTap?.cancel()
                        pendingSingleTap = null
                        onDoubleTap()
                    } else {
                        pendingSingleTap = launch {
                            delay(DOUBLE_TAP_WINDOW_MS)
                            onSingleTap()
                        }
                    }
                }
            }
        }
    }
}
