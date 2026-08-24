package com.rafambn.graphitesurface

import com.rafambn.scribe.Archivist
import com.rafambn.scribe.Scribe
import com.rafambn.scribe.seal
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.serialization.json.JsonPrimitive

internal class GraphiteEngineLogger(
    archivist: Archivist?,
    private val onArchiveFailure: (Throwable) -> Unit,
) {
    private val scribe: Scribe? = archivist?.let { configuredArchivist ->
        object : Scribe() {
            override val archivists: List<Archivist> = listOf(configuredArchivist)
            override val bufferCapacity: Int = 64
            override val bufferOverflow: BufferOverflow = BufferOverflow.DROP_OLDEST
            override val onArchiveFailure: ((Archivist, Map<String, kotlinx.serialization.json.JsonElement>, Throwable) -> Unit) =
                { _, _, error -> this@GraphiteEngineLogger.onArchiveFailure(error) }
        }.also(Scribe::hire)
    }

    internal fun emit(
        operation: String,
        outcome: String,
        fields: Map<String, Any?> = emptyMap(),
    ) {
        val activeScribe = scribe ?: return
        try {
            val scroll = activeScribe.newScroll()
            scroll["component"] = JsonPrimitive("graphite_surface")
            scroll["operation"] = JsonPrimitive(operation)
            scroll["outcome"] = JsonPrimitive(outcome)
            fields.forEach { (key, value) ->
                scroll[key] = when (value) {
                    null -> JsonPrimitive("null")
                    is Boolean -> JsonPrimitive(value)
                    is Number -> JsonPrimitive(value)
                    else -> JsonPrimitive(value.toString())
                }
            }
            scroll.seal(activeScribe)
        } catch (error: Throwable) {
            onArchiveFailure(error)
        }
    }

    internal suspend fun retire() {
        try {
            scribe?.retire()
        } catch (error: Throwable) {
            onArchiveFailure(error)
        }
    }
}
