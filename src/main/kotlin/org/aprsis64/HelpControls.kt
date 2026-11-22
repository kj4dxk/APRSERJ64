// SPDX-License-Identifier: AGPL-3.0-only
package org.aprsis64

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LocalHelpControl=staticCompositionLocalOf {false}

/** Hover help also works for disabled controls; wrapping keeps their click/focus behavior intact. */
@OptIn(ExperimentalFoundationApi::class)
@Composable internal fun HelpArea(help:String,modifier:Modifier=Modifier,content:@Composable ()->Unit) {
 TooltipArea(modifier=modifier,delayMillis=650,tooltip={
  Surface(color=Color(0xFF263740),contentColor=Color.White,elevation=8.dp,shape=RoundedCornerShape(6.dp)) {
   Text(help,Modifier.widthIn(max=380.dp).padding(12.dp),fontSize=13.sp)
  }
 }) {CompositionLocalProvider(LocalHelpControl provides true,content=content)}
}

@Composable internal fun HelpText(text:String,modifier:Modifier=Modifier,color:Color=Color.Unspecified,
 fontSize:TextUnit=TextUnit.Unspecified,fontWeight:FontWeight?=null,fontFamily:FontFamily?=null,
 textAlign:TextAlign?=null,maxLines:Int=Int.MAX_VALUE,overflow:TextOverflow=TextOverflow.Clip,
 style:TextStyle=LocalTextStyle.current,help:String=UiHelp.indicator(text)) {
 val render:@Composable ()->Unit={Text(text,color=color,fontSize=fontSize,fontWeight=fontWeight,fontFamily=fontFamily,textAlign=textAlign,maxLines=maxLines,overflow=overflow,style=style)}
 if(LocalHelpControl.current) Text(text,modifier,color=color,fontSize=fontSize,fontWeight=fontWeight,fontFamily=fontFamily,textAlign=textAlign,maxLines=maxLines,overflow=overflow,style=style)
 else HelpArea(help,modifier,render)
}

@Composable internal fun HelpButton(help:String,onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,
 contentPadding:PaddingValues=ButtonDefaults.ContentPadding,fill:Boolean=false,content:@Composable RowScope.()->Unit) {
 HelpArea(help,modifier) {Button(onClick,if(fill)Modifier.fillMaxWidth() else Modifier,enabled=enabled,contentPadding=contentPadding,content=content)}
}
@Composable internal fun HelpOutlinedButton(help:String,onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,
 contentPadding:PaddingValues=ButtonDefaults.ContentPadding,fill:Boolean=false,content:@Composable RowScope.()->Unit) {
 HelpArea(help,modifier) {OutlinedButton(onClick,if(fill)Modifier.fillMaxWidth() else Modifier,enabled=enabled,contentPadding=contentPadding,content=content)}
}
@Composable internal fun HelpTextButton(help:String,onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,
 contentPadding:PaddingValues=ButtonDefaults.TextButtonContentPadding,fill:Boolean=false,content:@Composable RowScope.()->Unit) {
 HelpArea(help,modifier) {TextButton(onClick,if(fill)Modifier.fillMaxWidth() else Modifier,enabled=enabled,contentPadding=contentPadding,content=content)}
}
@Composable internal fun HelpMenuItem(help:String,onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,content:@Composable RowScope.()->Unit) {
 HelpArea(help,modifier) {DropdownMenuItem(onClick,enabled=enabled,content=content)}
}
@Composable internal fun HelpField(help:String,value:String,onValueChange:(String)->Unit,modifier:Modifier=Modifier,
 enabled:Boolean=true,readOnly:Boolean=false,label:(@Composable ()->Unit)?=null,placeholder:(@Composable ()->Unit)?=null,
 isError:Boolean=false,visualTransformation:VisualTransformation=VisualTransformation.None,singleLine:Boolean=false,maxLines:Int=Int.MAX_VALUE,
 fill:Boolean=false) {
 HelpArea(help,modifier) {OutlinedTextField(value,onValueChange,modifier=if(fill)Modifier.fillMaxWidth() else Modifier,
  enabled=enabled,readOnly=readOnly,label=label,placeholder=placeholder,isError=isError,visualTransformation=visualTransformation,singleLine=singleLine,maxLines=maxLines)}
}
@Composable internal fun HelpCheckbox(help:String,checked:Boolean,onCheckedChange:((Boolean)->Unit)?,modifier:Modifier=Modifier,enabled:Boolean=true) {
 HelpArea(help,modifier) {Checkbox(checked,onCheckedChange,enabled=enabled)}
}
@Composable internal fun HelpSwitch(help:String,checked:Boolean,onCheckedChange:((Boolean)->Unit)?,modifier:Modifier=Modifier,enabled:Boolean=true) {
 HelpArea(help,modifier) {Switch(checked,onCheckedChange,enabled=enabled)}
}
