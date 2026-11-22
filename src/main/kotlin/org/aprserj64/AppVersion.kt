// SPDX-License-Identifier: AGPL-3.0-only
package org.aprserj64

internal object AppVersion {
    val value:String = checkNotNull(javaClass.getResourceAsStream("/version.txt")).bufferedReader().use {it.readText().trim()}
}
