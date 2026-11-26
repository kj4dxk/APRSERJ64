package org.aprserj64

import kotlin.math.*
import java.util.Locale

internal data class ReceiveArea(val north:Double,val west:Double,val south:Double,val east:Double) {
    fun contains(other:ReceiveArea)=north>=other.north && west<=other.west && south<=other.south && east>=other.east
    fun merge(other:ReceiveArea):ReceiveArea? {
        if(other.south>north || other.north<south || other.west>east || other.east<west)return null
        return ReceiveArea(max(north,other.north),min(west,other.west),min(south,other.south),max(east,other.east))
    }
    fun term()=String.format(Locale.ROOT,"a/%.3f/%.3f/%.3f/%.3f",north,west,south,east)
}

internal class ViewportSubscriptions(private val clock:()->Long={System.nanoTime()/1_000_000}){
    private val active=mutableMapOf<Any,List<ReceiveArea>>()
    private val recentlyClosed=linkedMapOf<ReceiveArea,Long>()
    fun update(window:Any,areas:List<ReceiveArea>) {
        val old=active.remove(window).orEmpty()
        val now=clock()
        old.filterNot {it in areas}.forEach {recentlyClosed[it]=now}
        if(areas.isNotEmpty())active[window]=areas
    }
    fun compose(base:String,limit:Int):String {
        recentlyClosed.entries.removeIf {clock()-it.value>=3_600_000}
        val order=active.values.flatten()+recentlyClosed.entries.sortedByDescending {it.value}.map {it.key}
        val merged=mutableListOf<ReceiveArea>()
        for(area in order.distinct()){
            var union=area
            var position=merged.size
            var i=0
            while(i<merged.size){
                val next=union.merge(merged[i])
                if(next==null){i++;continue}
                position=min(position,i)
                union=next
                merged.removeAt(i)
                i=0
            }
            merged.add(min(position,merged.size),union)
        }
        var result=base
        val remaining=(9-base.split(' ').count {it.removePrefix("-").startsWith("a/")}).coerceAtLeast(0)
        for(area in merged.take(remaining)){
            val term=area.term()
            if(result.length+term.length+1<=limit)result+=" "+term
        }
        return result
    }
}

/** MapLibre's north-up Mercator world is 512 logical pixels across at zoom zero. */
internal fun visibleAreas(lat:Double,lon:Double,zoom:Double,width:Double,height:Double):List<ReceiveArea> {
    val world=512*2.0.pow(zoom)
    val span=180*width/world
    if(width<=0 || height<=0 || span>=90) return emptyList()
    val y=asinh(tan(Math.toRadians(lat.coerceIn(-85.0511,85.0511))))
    fun latitude(delta:Double)=Math.toDegrees(atan(sinh(y+delta*PI/world)))
    val center=((lon+180)%360+360)%360-180
    val w=center-span
    val e=center+span
    fun area(left:Double,right:Double)=ReceiveArea(latitude(height),left,latitude(-height),right)
    return when {
        w < -180 -> listOf(area(w+360,180.0),area(-180.0,e))
        e > 180 -> listOf(area(w,180.0),area(-180.0,e-360))
        else -> listOf(area(w,e))
    }
}
