package uk.gov.defra.mmocatchrecord.common.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap

// Pixel checks need @GraphicsMode(NATIVE); Robolectric's legacy mode captures fully transparent images.
internal fun ImageBitmap.pixels(): List<Color> {
    val map = toPixelMap()
    return (0 until map.width).flatMap { x -> (0 until map.height).map { y -> map[x, y] } }
}

internal fun ImageBitmap.countOf(color: Color): Int = pixels().count { it == color }
