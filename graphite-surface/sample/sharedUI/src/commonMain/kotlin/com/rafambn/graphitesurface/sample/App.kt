@file:OptIn(com.rafambn.graphitesurface.ExperimentalGraphiteSurfaceApi::class)

package com.rafambn.graphitesurface.sample

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rafambn.graphitesurface.GraphiteSurface

@Composable
fun App() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        GraphiteSurface(
            modifier = Modifier.fillMaxSize(),
        )
        BasicText(
            text = "Graphite PoC",
            style = TextStyle(color = Color.Black, fontSize = 28.sp),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 120.dp),
        )
    }
}