// SPDX-License-Identifier: AGPL-3.0-only
package org.aprserj64

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import org.jetbrains.skia.EncodedImageFormat

/** Build-time export: Windows launcher and runtime icons share the same SVG artwork. */
fun main(args:Array<String>) {
    val sizes=listOf(16,24,32,48,64,128,256)
    val images=sizes.map {size->renderSvg("/aprserj64-logo.svg",size).use {image->
        checkNotNull(image.encodeToData(EncodedImageFormat.PNG)).use {it.bytes}
    }}
    val headerSize=6+16*sizes.size
    val buffer=ByteBuffer.allocate(headerSize+images.sumOf {it.size}).order(ByteOrder.LITTLE_ENDIAN)
    buffer.putShort(0).putShort(1).putShort(sizes.size.toShort())
    var offset=headerSize
    sizes.zip(images).forEach {(size,png)->
        buffer.put((size%256).toByte()).put((size%256).toByte()).put(0).put(0)
        buffer.putShort(1).putShort(32).putInt(png.size).putInt(offset)
        offset+=png.size
    }
    images.forEach {buffer.put(it)}
    Files.write(Path.of(args.single()),buffer.array())
}
