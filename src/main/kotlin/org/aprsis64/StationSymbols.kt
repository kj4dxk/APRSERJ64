// SPDX-License-Identifier: AGPL-3.0-only
package org.aprsis64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage

/** Renders the bundled APRS vectors at the resolution requested by each UI surface. */
object StationSymbols {
 private val labels=linkedMapOf<String,ImageBitmap>()
 @Synchronized fun label(text:String):ImageBitmap {
  labels[text]?.let {return it}
  val scratch=BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB);val measure=scratch.createGraphics()
  val font=java.awt.Font("SansSerif",java.awt.Font.BOLD,12);measure.font=font
  val lines=text.lines().take(3);val width=(lines.maxOfOrNull {measure.fontMetrics.stringWidth(it)} ?: 1)+8
  measure.dispose()
  // Symmetric transparent space keeps the label below the actual station coordinate.
  val height=lines.size*16+6;val image=BufferedImage(width,2*(height+17),BufferedImage.TYPE_INT_ARGB)
  val g=image.createGraphics();try {
   g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
   g.font=font;val y=image.height/2+17
   g.color=java.awt.Color(255,255,255,225);g.fillRoundRect(0,y,width,height,6,6)
   g.color=java.awt.Color(20,35,42);lines.forEachIndexed {i,line->g.drawString(line,(width-g.fontMetrics.stringWidth(line))/2,y+14+i*16)}
  }finally {g.dispose()}
  if(labels.size>=500)labels.remove(labels.keys.first())
  return image.toComposeImageBitmap().also {labels[text]=it}
 }
 private data class SymbolKey(val table: Char, val code: Int, val pixels: Int)

 private val images = object : LinkedHashMap<SymbolKey, ImageBitmap>(256, .75f, true) {
  override fun removeEldestEntry(eldest: MutableMap.MutableEntry<SymbolKey, ImageBitmap>?): Boolean =
   size > 512
 }

 @Synchronized
 fun bitmap(table: Char, symbol: Char, pixels: Int = 24): ImageBitmap {
  val effectiveTable = when (table) {
   '/', in 'A'..'Z', in '0'..'9' -> table
   else -> '\\'
  }
  val key = SymbolKey(effectiveTable, symbol.code.coerceIn(33, 126), pixels)
  return images.getOrPut(key) {
   render(key.table, key.code, key.pixels).use { image ->
    if (key.table == '/' || key.table == '\\') {
     image.toComposeImageBitmap()
    } else {
     val awt = image.toAwtImage()
     val graphics = awt.createGraphics()
     try {
      graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
      graphics.scale(pixels / 24.0, pixels / 24.0)
      graphics.font = Font("SansSerif", Font.BOLD, 13)
      val text = key.table.toString()
      val width = graphics.fontMetrics.stringWidth(text)
      val left = (24 - width) / 2
      graphics.color = Color.WHITE
      graphics.fillRect(left - 1, 2, width + 2, 14)
      graphics.color = Color.BLACK
      graphics.drawString(text, left, 14)
     } finally {
      graphics.dispose()
     }
     awt.toComposeImageBitmap()
    }
   }
  }
 }

 private fun render(table: Char, code: Int, pixels: Int): Image {
  val directory = if (table == '/') "primary" else "alternate"
  return renderSvg("/aprs-symbols/$directory/${code.toString(16)}.svg", pixels)
 }
}
