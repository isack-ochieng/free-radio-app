package tech.iox.freeradio.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import tech.iox.freeradio.core.RadioFrequency
import tech.iox.freeradio.ui.theme.RadioAccent
import tech.iox.freeradio.ui.theme.RadioMuted
import tech.iox.freeradio.ui.theme.RadioSurfaceSoft
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun FrequencyDial(
    frequencyMhz: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier.fillMaxWidth().height(142.dp)) {
        val center = Offset(size.width / 2f, size.height * 0.82f)
        val radius = size.width * 0.405f

        drawArc(
            color = RadioSurfaceSoft,
            startAngle = 200f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2f, radius * 2f),
            style = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round)
        )

        for (index in 0..40) {
            val angle = Math.toRadians(200.0 + 140.0 * index / 40.0)
            val outer = Offset(
                center.x + cos(angle).toFloat() * radius,
                center.y + sin(angle).toFloat() * radius
            )
            val major = index % 5 == 0
            val innerRadius = radius - if (major) 18.dp.toPx() else 10.dp.toPx()
            val inner = Offset(
                center.x + cos(angle).toFloat() * innerRadius,
                center.y + sin(angle).toFloat() * innerRadius
            )
            drawLine(
                color = RadioMuted.copy(alpha = if (major) 0.9f else 0.45f),
                start = inner,
                end = outer,
                strokeWidth = if (major) 2.dp.toPx() else 1.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        val pointerAngle = Math.toRadians(
            200.0 + 140.0 * RadioFrequency.fraction(frequencyMhz)
        )
        val pointerEnd = Offset(
            center.x + cos(pointerAngle).toFloat() * (radius - 26.dp.toPx()),
            center.y + sin(pointerAngle).toFloat() * (radius - 26.dp.toPx())
        )
        drawLine(
            color = RadioAccent,
            start = center,
            end = pointerEnd,
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawCircle(color = RadioAccent, radius = 5.dp.toPx(), center = center)
    }
}
