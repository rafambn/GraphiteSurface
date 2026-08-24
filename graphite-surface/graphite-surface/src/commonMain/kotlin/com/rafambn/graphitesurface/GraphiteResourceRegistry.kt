@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package com.rafambn.graphitesurface

import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicLong
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class GraphiteResourceRegistry : AutoCloseable {
    private val mutex: Mutex = Mutex()
    private val closed: AtomicBoolean = AtomicBoolean(false)
    private val nextId: AtomicLong = AtomicLong(0)
    private val entries: MutableMap<GraphiteCommandProgram, Entry> = mutableMapOf()
    private val registeredResources: AtomicLong = AtomicLong(0)
    private val registeredResourceBytes: AtomicLong = AtomicLong(0)
    private val resourcePublications: AtomicLong = AtomicLong(0)
    private val publishedResourceBytes: AtomicLong = AtomicLong(0)
    private val resourceCacheHits: AtomicLong = AtomicLong(0)
    private val releasedResources: AtomicLong = AtomicLong(0)
    private val workerMessageBytes: AtomicLong = AtomicLong(0)
    private val totalPreparationNanos: AtomicLong = AtomicLong(0)
    private val maximumPreparationNanos: AtomicLong = AtomicLong(0)
    private val totalValidationNanos: AtomicLong = AtomicLong(0)
    private val maximumValidationNanos: AtomicLong = AtomicLong(0)

    internal suspend fun prepare(
        program: GraphiteCommandProgram,
        workerIndex: Int,
    ): ByteArray = mutex.withLock {
        val preparationStarted = platformMonotonicNanos()
        check(!closed.load()) { "resource registry is closed" }
        validateCommands(program)
        validateNestingDepth(program)
        val publications = mutableListOf<GraphiteWorkerPublication>()
        val rootResourceIds = program.resources.map { resource ->
            register(resource, workerIndex, publications)
        }.toLongArray()
        GraphiteWorkerMessage.encode(program, rootResourceIds, publications).also { message ->
            workerMessageBytes.addAndFetch(message.size.toLong())
            val elapsed = elapsedSince(preparationStarted)
            totalPreparationNanos.addAndFetch(elapsed)
            maximumPreparationNanos.updateMaximum(elapsed)
        }
    }

    internal fun snapshot(): GraphiteMetricsSnapshot.Resources = GraphiteMetricsSnapshot.Resources(
        registered = registeredResources.load(),
        registeredBytes = registeredResourceBytes.load(),
        publications = resourcePublications.load(),
        publishedBytes = publishedResourceBytes.load(),
        cacheHits = resourceCacheHits.load(),
        released = releasedResources.load(),
        workerMessageBytes = workerMessageBytes.load(),
        totalPreparationNanos = totalPreparationNanos.load(),
        maximumPreparationNanos = maximumPreparationNanos.load(),
        totalValidationNanos = totalValidationNanos.load(),
        maximumValidationNanos = maximumValidationNanos.load(),
    )

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        val released = entries.size
        entries.clear()
        releasedResources.addAndFetch(released.toLong())
    }

    private fun register(
        program: GraphiteCommandProgram,
        workerIndex: Int,
        publications: MutableList<GraphiteWorkerPublication>,
    ): Long {
        val existing = entries[program]
        val entry = if (existing == null) {
            validateCommands(program)
            val childIds = program.resources.map { child ->
                register(child, workerIndex, publications)
            }.toLongArray()
            Entry(
                id = nextId.addAndFetch(1),
                program = program,
                childIds = childIds,
            ).also { created ->
                entries[program] = created
                registeredResources.addAndFetch(1)
                registeredResourceBytes.addAndFetch(program.commands.size.toLong())
            }
        } else {
            resourceCacheHits.addAndFetch(1)
            existing.program.resources.forEach { child ->
                register(child, workerIndex, publications)
            }
            existing
        }
        if (entry.publishedWorkers.add(workerIndex)) {
            val commands = entry.program.commands
            publications += GraphiteWorkerPublication(entry.id, commands, entry.childIds)
            resourcePublications.addAndFetch(1)
            publishedResourceBytes.addAndFetch(commands.size.toLong())
        }
        return entry.id
    }

    private fun validateNestingDepth(program: GraphiteCommandProgram, remainingDepth: Int = 64) {
        require(remainingDepth > 0) { "display-list nesting exceeds 64 levels" }
        program.resources.forEach { child ->
            validateNestingDepth(child, remainingDepth - 1)
        }
    }

    private fun validateCommands(program: GraphiteCommandProgram) {
        val started = platformMonotonicNanos()
        GraphiteCommandBuffer.validate(program.commands, program.resources.size)
        val elapsed = elapsedSince(started)
        totalValidationNanos.addAndFetch(elapsed)
        maximumValidationNanos.updateMaximum(elapsed)
    }

    private fun elapsedSince(started: Long): Long =
        (platformMonotonicNanos() - started).coerceAtLeast(0)

    private class Entry(
        val id: Long,
        val program: GraphiteCommandProgram,
        val childIds: LongArray,
        val publishedWorkers: MutableSet<Int> = mutableSetOf(),
    )
}

private fun AtomicLong.updateMaximum(candidate: Long) {
    while (true) {
        val current = load()
        if (candidate <= current || compareAndSet(current, candidate)) return
    }
}
