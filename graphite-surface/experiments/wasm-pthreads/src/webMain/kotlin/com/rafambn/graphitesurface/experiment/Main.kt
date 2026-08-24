package com.rafambn.graphitesurface.experiment

import kotlinx.browser.document

public fun main() {
    document.getElementById("status")?.textContent =
        "Kotlin host ready; native pthread probe has not started."
}
