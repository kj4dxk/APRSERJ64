// SPDX-License-Identifier: AGPL-3.0-only
package org.aprsis64
import kotlin.test.*
import net.osmand.shared.aprs.AprsConnectionState

class CoordinateInputTest {
 private fun point(text:String,lat:Double,lon:Double,reference:MapLocation?=null):CoordinateResult {
  val r=CoordinateInput.parse(text,reference)
  assertNotNull(r.location,"$text: ${r.error}")
  assertEquals(lat,r.location.latitude,0.000001,text);assertEquals(lon,r.location.longitude,0.000001,text)
  return r
 }
 @Test fun decimalAndPunctuation() {
  for(s in listOf("28.536725, -81.242932","  ( 28.536725 ; -81.242932 )  ","lat: 28.536725 lon: -81.242932","+28.536725-081.242932/","28,536725 -81,242932","28.536725 | -81.242932")) point(s,28.536725,-81.242932)
 }
 @Test fun degreeMinuteSecondVariants() {
  val lat=28+32.0/60+12.21/3600;val lon=-(81+14.0/60+34.56/3600)
  for(s in listOf("28°32'12.21\"N 81°14'34.56\"W","N 28 32 12.21 W 81 14 34.56","81°14′34.56″W, 28°32′12.21″N","28 32 12.21, -81 14 34.56"))point(s,lat,lon)
  point("28 32.20 N 81 14.58 W",28+32.2/60,-(81+14.58/60))
  point("2832.20N/08114.58W",28+32.2/60,-(81+14.58/60))
  point("0045.00N/00030.00E",0.75,0.5)
  point("0030.00S/00045.00W",-0.5,-0.75)
  assertNull(CoordinateInput.parse("0090.00N/00030.00E").location)
  point("28 32.20, -81 14.58",28+32.2/60,-(81+14.58/60))
  point("N28.5, 81.2W",28.5,-81.2)
  point("28.5N W81.2",28.5,-81.2)
  point("N28 32.20, 81 14.58W",28+32.2/60,-(81+14.58/60))
 }
 @Test fun limitedTypingRepairsAreVisible() {
  for(s in listOf("28.5367O5, -8I.242932","28..536705, -81.242932","28.53!6705, -81.242932","28 . 536705, -81. 242932")) {
   assertTrue(point(s,28.536705,-81.242932).explanation.contains("corrected"))
  }
  assertTrue(point("-122.3, 47.6",47.6,-122.3).explanation.contains("Longitude-first"))
 }
 @Test fun signedZeroAndBoundaries() {
  point("-0 30, -0 30",-.5,-.5)
  point("90, 180",90.0,180.0);point("90 S 180 W",-90.0,-180.0)
  point("0, 0",0.0,0.0)
 }
 @Test fun malformedAndAmbiguousInputsFail() {
  for(s in listOf("","95,100","28 60 N 81 14 W","28 32 60 N 81 14 34 W","-28 N 81 W","28 N 81 S","hello 28 -81","28,5,-81,2","NaN,NaN","28.5","28.5, -81.2, 200","28.5abc -81.2","28.5; -181","28.1 32 N 81 14 W"))
   assertNull(CoordinateInput.parse(s).location,s)
 }
 @Test fun googleCoordinateLinks() {
  for(s in listOf(
   "https://www.google.com/maps/search/?api=1&query=28.536725%2C-81.242932",
   "https://maps.google.com/?q=28.536725,+-81.242932",
   "https://www.google.com/maps/@28.536725,-81.242932,15z",
   "https://www.google.com/maps/place/example/@30,-80,10z/data=!3d28.536725!4d-81.242932",
   "https://www.google.com/maps/place/28.536725,-81.242932/",
   "https://www.google.com/maps/dir/?api=1&destination=28.536725%2C-81.242932"))point(s,28.536725,-81.242932)
  for(s in listOf("https://maps.app.goo.gl/12345","https://evil.example/?q=28,-81","https://www.google.com/maps/?q=123+Main+Street","https://www.google.com/maps/data=!3d28!4d-81!3d30!4d-80"))assertNull(CoordinateInput.parse(s).location,s)
 }
 @Test fun fullPlusCodesUseAreaCenter() {
  point("849VCWC8+R9",37.4220625,-122.0840625)
  point("849vcwc8 + r9",37.4220625,-122.0840625)
  point("https://www.google.com/maps/search/?api=1&query=849VCWC8%2BR9",37.4220625,-122.0840625)
  assertNotNull(CoordinateInput.parse("849V0000+").location)
 }
 @Test fun shortPlusCodesRequireAndExplainReference() {
  assertNull(CoordinateInput.parse("CWC8+R9").location)
  val r=point("CWC8+R9",37.4220625,-122.0840625,MapLocation(37.42,-122.08))
  assertTrue(r.explanation.contains("reference location"));assertTrue(r.explanation.contains("849VCWC8+R9"))
  assertNull(CoordinateInput.parse("CWC8+R9 Mountain View",MapLocation(28.5,-81.2)).location)
 }
 @Test fun statusLabelsDistinguishPracticeAndVerification() {
  assertEquals("APRS-IS connected and verified",connectionLabel(AprsConnectionState.VERIFIED))
  assertTrue(connectionLabel(AprsConnectionState.RECEIVING).contains("receive only"))
  assertTrue(connectionLabel(AprsConnectionState.VERIFIED,true).contains("local traffic only"))
  assertFalse(connectionLabel(AprsConnectionState.VERIFIED,true).contains("verified"))
 }
}
