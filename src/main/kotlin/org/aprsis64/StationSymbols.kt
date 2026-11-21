// SPDX-License-Identifier: AGPL-3.0-only
package org.aprsis64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

/** APRSISMO symbol sheets, AGPL-3.0; same 16-column, 21-pixel cell layout. */
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
