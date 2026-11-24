// SPDX-License-Identifier: AGPL-3.0-only
package org.aprserj64

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import java.awt.GraphicsEnvironment
import java.awt.KeyboardFocusManager
import java.awt.Rectangle
import java.awt.Toolkit

internal fun fitWindow(area: Rectangle, width: Int, height: Int, offset: Int = 0): Rectangle {
    val w=width.coerceIn(1,area.width)
    val h=height.coerceIn(1,area.height)
    val x=(area.x+(area.width-w)/2+offset).coerceIn(area.x,area.x+area.width-w)
    val y=(area.y+(area.height-h)/2+offset).coerceIn(area.y,area.y+area.height-h)
    return Rectangle(x,y,w,h)
}

internal fun windowBounds(width: Int, height: Int, offset: Int = 0): Rectangle {
    val monitor=KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow?.graphicsConfiguration
        ?: GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration
    val screen=monitor.bounds
    val insets=Toolkit.getDefaultToolkit().getScreenInsets(monitor)
    return fitWindow(Rectangle(screen.x+insets.left,screen.y+insets.top,
        screen.width-insets.left-insets.right,screen.height-insets.top-insets.bottom),width,height,offset)
}

internal val Rectangle.composePosition get()=WindowPosition.Absolute(x.dp,y.dp)
