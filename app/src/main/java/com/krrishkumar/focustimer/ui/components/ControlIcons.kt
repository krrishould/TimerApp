package com.krrishkumar.focustimer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Control glyphs drawn directly rather than pulled from material-icons-extended,
 * which the project deliberately avoids (large, and not present in the local cache).
 */

@Composable
fun PlayIcon(color: Color, size: Dp = 22.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val path = Path().apply {
            moveTo(w * 0.24f, h * 0.12f)
            lineTo(w * 0.86f, h * 0.5f)
            lineTo(w * 0.24f, h * 0.88f)
            close()
        }
        drawPath(path, color)
    }
}

@Composable
fun PauseIcon(color: Color, size: Dp = 22.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val barWidth = w * 0.22f
        drawRect(color, Offset(w * 0.2f, h * 0.13f), Size(barWidth, h * 0.74f))
        drawRect(color, Offset(w * 0.58f, h * 0.13f), Size(barWidth, h * 0.74f))
    }
}

/** Gear: a stroked ring with eight short teeth radiating from it. */
@Composable
fun GearIcon(color: Color, size: Dp = 19.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val centre = Offset(w / 2f, w / 2f)
        val stroke = w * 0.13f
        val ringRadius = w * 0.28f
        val toothOuter = w * 0.46f

        repeat(8) { index ->
            rotate(degrees = index * 45f, pivot = centre) {
                drawLine(
                    color = color,
                    start = Offset(centre.x, centre.y - ringRadius),
                    end = Offset(centre.x, centre.y - toothOuter),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
            }
        }
        drawCircle(color, radius = ringRadius, center = centre, style = Stroke(width = stroke))
    }
}

/** Small pencil, angled from bottom-left to top-right. */
@Composable
fun PencilIcon(color: Color, size: Dp = 13.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        // body of the pencil
        val body = Path().apply {
            moveTo(w * 0.28f, h * 0.72f)
            lineTo(w * 0.72f, h * 0.28f)
            lineTo(w * 0.9f, h * 0.46f)
            lineTo(w * 0.46f, h * 0.9f)
            close()
        }
        drawPath(body, color)

        // tip
        val tip = Path().apply {
            moveTo(w * 0.1f, h * 0.9f)
            lineTo(w * 0.34f, h * 0.84f)
            lineTo(w * 0.16f, h * 0.66f)
            close()
        }
        drawPath(tip, color)
    }
}

/** Circular arrow: an open arc plus a small arrowhead at its leading end. */
@Composable
fun ResetIcon(color: Color, size: Dp = 20.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = w * 0.12f
        val inset = stroke / 2f + w * 0.08f

        drawArc(
            color = color,
            startAngle = 70f,
            sweepAngle = 290f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = Size(w - inset * 2, h - inset * 2),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )

        // arrowhead at the arc's open end (top-right)
        val headSize = w * 0.24f
        val cx = w * 0.78f
        val cy = h * 0.20f
        val head = Path().apply {
            moveTo(cx, cy - headSize * 0.55f)
            lineTo(cx + headSize * 0.5f, cy + headSize * 0.5f)
            lineTo(cx - headSize * 0.5f, cy + headSize * 0.45f)
            close()
        }
        drawPath(head, color)
    }
}
