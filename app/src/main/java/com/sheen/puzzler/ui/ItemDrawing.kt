package com.sheen.puzzler.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.sheen.puzzler.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The six item images in color-index order: fries, burger, pizza, potted plant, soda can, chocolate bar
 * (docs/SPEC.md §8). Rendered from art/items/ by scripts/render-items.py.
 */
@Composable
fun rememberItemImages(): List<ImageBitmap> =
    listOf(
        ImageBitmap.imageResource(R.drawable.item_fries),
        ImageBitmap.imageResource(R.drawable.item_burger),
        ImageBitmap.imageResource(R.drawable.item_pizza),
        ImageBitmap.imageResource(R.drawable.item_plant),
        ImageBitmap.imageResource(R.drawable.item_soda),
        ImageBitmap.imageResource(R.drawable.item_chocolate),
    )

/**
 * Color bomb geometry shared by the board and the launch intro. The launcher icon (rendered by
 * scripts/render-items.py) and splash drawable use the same proportions (docs/SPEC.md §6).
 */
internal object ColorBomb {
    /** Item centers sit on a ring of this radius, as a fraction of the orb radius. */
    const val RING = 0.6f

    /** Mini item square side as a fraction of the orb radius. */
    const val ITEM = 0.42f

    /** Sparkle outer radius as a fraction of the orb radius. */
    const val SPARKLE = 0.23f

    /** Angle of item [index] on the ring: the first at the top, then clockwise. */
    fun angle(index: Int): Double = -PI / 2 + 2 * PI * index / PuzzlerColors.pieces.size

    /** Unit direction from the orb center toward item [index]. */
    fun direction(index: Int) = Offset(cos(angle(index)).toFloat(), sin(angle(index)).toFloat())
}

/** Draws item [index] filling a [size]-wide square centered on [center], over a soft shadow when [shadow] is set. */
internal fun DrawScope.drawItem(
    images: List<ImageBitmap>,
    index: Int,
    center: Offset,
    size: Float,
    alpha: Float = 1f,
    shadow: Boolean = true,
) {
    if (shadow) {
        // A soft oval under the item's base so it sits on the tile.
        val shadowCenter = center + Offset(0f, size * 0.4f)
        val shadowSize = Size(size * 0.78f, size * 0.18f)
        drawOval(
            Brush.radialGradient(
                listOf(Color.Black.copy(alpha = 0.35f * alpha), Color.Transparent),
                center = shadowCenter,
                radius = shadowSize.width / 2,
            ),
            topLeft = shadowCenter - Offset(shadowSize.width / 2, shadowSize.height / 2),
            size = shadowSize,
        )
    }
    val image = images[index % images.size]
    val side = size.roundToInt()
    drawImage(
        image,
        dstOffset = IntOffset((center.x - size / 2).roundToInt(), (center.y - size / 2).roundToInt()),
        dstSize = IntSize(side, side),
        alpha = alpha,
        filterQuality = FilterQuality.Medium,
    )
}

/** Draws the color bomb: flat orb, the six mini items on their ring, and the gold sparkle core. */
internal fun DrawScope.drawColorBomb(
    images: List<ImageBitmap>,
    center: Offset,
    radius: Float,
    alpha: Float = 1f,
) {
    drawCircle(PuzzlerColors.bombOrb.copy(alpha = alpha), radius, center)
    images.indices.forEach { i ->
        drawItem(images, i, center + ColorBomb.direction(i) * (radius * ColorBomb.RING), radius * ColorBomb.ITEM, alpha, shadow = false)
    }
    drawPath(sparklePath(center, radius * ColorBomb.SPARKLE), PuzzlerColors.gold.copy(alpha = alpha))
}

/** Four-point sparkle: the color bomb's core. */
internal fun sparklePath(
    c: Offset,
    r: Float,
): Path =
    Path().apply {
        for (i in 0 until 8) {
            val rr = if (i % 2 == 0) r else r * 0.3f
            val a = -PI / 2 + i * PI / 4
            val p = Offset(c.x + (cos(a) * rr).toFloat(), c.y + (sin(a) * rr).toFloat())
            if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
        }
        close()
    }
