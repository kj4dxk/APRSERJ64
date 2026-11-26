// SPDX-License-Identifier: AGPL-3.0-only
package org.aprserj64

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.Colors
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

internal fun useLightTheme(preference: String?, systemDark: Boolean): Boolean = when (preference) {
    "light" -> true
    "dark" -> false
    else -> !systemDark
}

@Composable
internal fun applicationColors(model: AppModel): Colors {
    val revision by model.settingsRevision.collectAsState()
    val choice = remember(revision) { model.settings.getProperty("display.theme") }
    return if (useLightTheme(choice, isSystemInDarkTheme())) {
        lightColors(primary = Color(0xFF006349), secondary = Color(0xFF006C76), surface = Color(0xFFF1F6F5))
    } else {
        darkColors(primary = Color(0xFF72E4C3), secondary = Color(0xFF80D4DB),
            background = Color(0xFF0E1B22), surface = Color(0xFF15252D))
    }
}
