// SPDX-License-Identifier: AGPL-3.0-only
package org.aprserj64

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable internal fun ScrollableForm(modifier: Modifier, content: @Composable ColumnScope.()->Unit) {
    val scroll=rememberScrollState()
    Box(modifier) {
        Column(Modifier.fillMaxSize().padding(end=14.dp).verticalScroll(scroll),
            verticalArrangement=Arrangement.spacedBy(8.dp),content=content)
        VerticalScrollbar(rememberScrollbarAdapter(scroll),Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}
