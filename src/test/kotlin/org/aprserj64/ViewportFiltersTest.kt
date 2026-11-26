package org.aprserj64

import kotlin.test.*

class ViewportFiltersTest {
    @Test fun overlapExpiryPriorityAndBudget(){
        var time=0L
        val subscriptions=ViewportSubscriptions {time}
        val left=ReceiveArea(10.0,0.0,0.0,10.0)
        val right=ReceiveArea(10.0,8.0,0.0,18.0)
        subscriptions.update("one",listOf(left))
        subscriptions.update("two",listOf(right))
        assertEquals("g/ME "+left.merge(right)!!.term(),subscriptions.compose("g/ME",440))
        subscriptions.update("one",emptyList())
        time=3_599_999
        assertEquals("g/ME "+left.merge(right)!!.term(),subscriptions.compose("g/ME",440))
        time++
        assertEquals("g/ME "+right.term(),subscriptions.compose("g/ME",440))
        assertEquals("g/ME",subscriptions.compose("g/ME",10))
        repeat(12){i->subscriptions.update(i,listOf(ReceiveArea(1.0,-100.0+i*2,0.0,-99.0+i*2)))}
        assertEquals(9,subscriptions.compose("g/ME a/2/1/0/3",440).split(" ").count {it.startsWith("a/")})
    }
    @Test fun splitsAntimeridianAndSkipsWorldView() {
        val east=visibleAreas(40.0,179.9,8.0,1000.0,600.0)
        val west=visibleAreas(40.0,-180.1,8.0,1000.0,600.0)
        assertEquals(2,east.size)
        assertEquals(east.map {it.term()},west.map {it.term()})
        assertTrue(east.all {it.north>40 && it.south<40 && it.west<it.east})
        assertTrue(visibleAreas(0.0,0.0,0.0,1200.0,600.0).isEmpty())
    }
}
