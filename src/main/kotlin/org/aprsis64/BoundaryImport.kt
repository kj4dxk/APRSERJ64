// SPDX-License-Identifier: AGPL-3.0-only
package org.aprsis64
import java.nio.file.*
import java.util.zip.ZipInputStream

object BoundaryImport {
 fun copy(home:Path,input:Path,column:String):List<String> {
  val root=home.resolve("data/boundaries");Files.createDirectories(root)
  val folder=Files.createTempDirectory(root,"set-");var total=0L
  if(input.toString().endsWith(".zip",true)) {
   require(Files.size(input)<=256_000_000){"Boundary archive exceeds 256 MB"}
   ZipInputStream(Files.newInputStream(input)).use {zip->var count=0
    while(true){val entry=zip.nextEntry?:break;if(entry.isDirectory)continue;require(++count<=128){"Too many archive entries"}
     val name=entry.name.replace('\\','/').substringAfterLast('/').lowercase()
     if(name.substringAfterLast('.') !in setOf("shp","dbf","prj"))continue
     require(name.matches(Regex("[a-z0-9_.-]{1,120}")) && name !in listOf(".",".."))
     Files.newOutputStream(folder.resolve(name),StandardOpenOption.CREATE_NEW).use {out->val buffer=ByteArray(8192);while(true){val n=zip.read(buffer);if(n<0)break;total+=n;require(total<=384_000_000){"Expanded boundary archive too large"};out.write(buffer,0,n)}}
    }
   }
  }else {
   require(input.toString().endsWith(".shp",true))
   val stem=input.fileName.toString().dropLast(4)
   Files.list(input.toAbsolutePath().parent).use {files->files.filter {it.fileName.toString().substringBeforeLast('.').equals(stem,true) && it.fileName.toString().substringAfterLast('.').lowercase() in setOf("shp","dbf","prj")}.forEach {file->
    total+=Files.size(file);require(total<=384_000_000);Files.copy(file,folder.resolve(file.fileName.toString().lowercase()))
   }}
  }
  val shapes=Files.list(folder).use {it.filter {p->p.toString().endsWith(".shp")}.toList()};require(shapes.isNotEmpty() && shapes.size<=12){"Choose a ZIP with paired SHP/DBF files"}
  shapes.forEach {shape->
   val prj=Path.of(shape.toString().dropLast(4)+".prj")
   if(Files.exists(prj)){val projection=Files.readString(prj);require(!projection.contains("PROJCS",true) && !projection.contains("PROJCRS",true)){"Reproject boundaries to longitude/latitude before importing"}}
   require(ShapeBoundaries.read(shape,column).isNotEmpty()){"No UGC zone/county codes found; check DBF column"}
  }
  return shapes.map {home.toAbsolutePath().normalize().relativize(it.toAbsolutePath().normalize()).toString()}
 }
}
