// SPDX-License-Identifier: AGPL-3.0-only
package org.aprsis64

import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Data
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Surface
import org.jetbrains.skia.svg.SVGDOM
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

internal object AppIcon {
    private const val RESOURCE = "/aprsis64-logo.svg"

    val window by lazy {
        renderSvg(RESOURCE, 64).use { BitmapPainter(it.toComposeImageBitmap()) }
    }

    fun tray(pixels: Int): BufferedImage =
        renderSvg(RESOURCE, pixels).use { it.toAwtImage() }
}

internal fun renderSvg(resource: String, pixels: Int): Image {
    val bytes = checkNotNull(AppIcon::class.java.getResourceAsStream(resource)) {
        "Missing SVG resource $resource"
    }.use { it.readBytes() }

    return Data.makeFromBytes(bytes).use { data ->
        SVGDOM(data).use { svg ->
            Surface.makeRasterN32Premul(pixels, pixels).use { surface ->
                svg.setContainerSize(pixels.toFloat(), pixels.toFloat())
                svg.render(surface.canvas)
                surface.makeImageSnapshot()
            }
        }
    }
}

internal fun Image.toAwtImage(): BufferedImage =
    checkNotNull(encodeToData(EncodedImageFormat.PNG)).use { png ->
        ImageIO.read(png.bytes.inputStream())
    }
