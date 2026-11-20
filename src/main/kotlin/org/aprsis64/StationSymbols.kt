// SPDX-License-Identifier: AGPL-3.0-only
package org.aprsis64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

/** APRSISMO symbol sheets, AGPL-3.0; same 16-column, 21-pixel cell layout. */
object StationSymbols {
 private val primary=ImageIO.read(javaClass.getResource("/APRS1.png"))
 private val alternate=ImageIO.read(javaClass.getResource("/APRS2.png"))
 private val images=mutableMapOf<String,ImageBitmap>()
 @Synchronized fun bitmap(table:Char,symbol:Char):ImageBitmap = images.getOrPut("$table$symbol") {
  val index=(symbol.code-33).coerceIn(0,93);val sheet=if(table=='/') primary else alternate
  val image=BufferedImage(24,24,BufferedImage.TYPE_INT_ARGB)
  val g=image.createGraphics()
  try {
   val x=2+(index%16)*21;val y=2+(index/16)*21
   g.drawImage(sheet.getSubimage(x,y,18,18),0,0,24,24,null)
   if(table in 'A'..'Z' || table in '0'..'9') {
    g.font=java.awt.Font("SansSerif",java.awt.Font.BOLD,13)
    val text=table.toString();val xx=(24-g.fontMetrics.stringWidth(text))/2
    g.color=java.awt.Color.WHITE;g.fillRect(xx-1,2,g.fontMetrics.stringWidth(text)+2,14)
    g.color=java.awt.Color.BLACK;g.drawString(text,xx,14)
   }
  } finally {g.dispose()}
  image.toComposeImageBitmap()
 }
}
