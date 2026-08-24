@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.rafambn.graphitesurface.sample

import androidx.compose.ui.window.ComposeViewport

fun main() {
    ComposeViewport(
        viewportContainerId = "root",
        content = { App() },
    )
}
