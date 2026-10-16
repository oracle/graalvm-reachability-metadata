/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_network_jvm

import io.ktor.network.selector.ClosedChannelCancellationException
import io.ktor.network.selector.InterestSuspensionsMap
import io.ktor.network.selector.SelectInterest
import io.ktor.network.selector.Selectable
import io.ktor.network.selector.SelectorManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.channels.Pipe
import java.nio.channels.SelectableChannel
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resumeWithException

/**
 * Bridges raw java.nio pipe channels into coroutines through the ktor selector manager, using
 * selectable registrations with READ and WRITE interests.
 *
 * A job runner streams progress events into a pipe faster than the dashboard drains them, so
 * the writer has to suspend on WRITE readiness while the reader suspends on READ readiness,
 * and closing an idle pipe end releases a consumer still waiting for data.
 */
public class PipeEventBridgeTest {

    /** One end of a pipe registered with the selector, as an application adapter would. */
    private class PipeEnd(
        override val channel: SelectableChannel,
        private val selector: SelectorManager,
    ) : Selectable {

        private val interests: AtomicInteger = AtomicInteger(0)
        private val closed: AtomicBoolean = AtomicBoolean(false)

        override val suspensions: InterestSuspensionsMap = InterestSuspensionsMap()

        override val isClosed: Boolean
            get() = closed.get()

        override val interestedOps: Int
            get() = interests.get()

        init {
            channel.configureBlocking(false)
        }

        override fun interestOp(interest: SelectInterest, state: Boolean) {
            interests.updateAndGet { ops ->
                if (state) ops or interest.flag else ops and interest.flag.inv()
            }
        }

        /** Suspends until the selector reports the channel ready for [interest]. */
        suspend fun awaitReady(interest: SelectInterest) {
            interestOp(interest, true)
            try {
                selector.select(this, interest)
            } finally {
                interestOp(interest, false)
            }
        }

        override fun close() {
            if (!closed.compareAndSet(false, true)) {
                return
            }
            channel.close()
            interests.set(0)
            for (interest in SelectInterest.entries) {
                suspensions.removeSuspension(interest)
                    ?.resumeWithException(ClosedChannelCancellationException())
            }
            selector.notifyClosed(this)
        }

        override fun dispose() {
            close()
        }
    }

    /** Totals the dashboard observed after draining the pipe. */
    private data class Drained(val events: Int, val lastProgressByJob: Map<String, Int>)

    @Test
    fun streamsJobProgressThroughSelectorDrivenPipe(): Unit = runBlocking {
        val selector: SelectorManager = SelectorManager(Dispatchers.IO)
        try {
            val pipe: Pipe = selector.provider.openPipe()
            val sink: PipeEnd = PipeEnd(pipe.sink(), selector)
            val source: PipeEnd = PipeEnd(pipe.source(), selector)
            val payload: ByteBuffer =
                ByteBuffer.wrap(progressEvents().toByteArray(StandardCharsets.US_ASCII))
            val totalBytes: Int = payload.remaining()
            val writerBlocked: CompletableDeferred<Unit> = CompletableDeferred()

            withTimeout(30_000) {
                val writer = async(Dispatchers.IO) {
                    var writeWaits = 0
                    sink.use {
                        while (payload.hasRemaining()) {
                            val written: Int = pipe.sink().write(payload)
                            if (written == 0) {
                                writeWaits++
                                writerBlocked.complete(Unit)
                                sink.awaitReady(SelectInterest.WRITE)
                            }
                        }
                    }
                    writerBlocked.complete(Unit)
                    writeWaits
                }

                val reader = async(Dispatchers.IO) {
                    // The dashboard starts late: the runner fills the pipe and waits for space.
                    writerBlocked.await()
                    val text = StringBuilder()
                    val chunk: ByteBuffer = ByteBuffer.allocate(4096)
                    var readBytes = 0
                    source.use {
                        while (true) {
                            chunk.clear()
                            val read: Int = pipe.source().read(chunk)
                            when {
                                read < 0 -> break
                                read == 0 -> source.awaitReady(SelectInterest.READ)
                                else -> {
                                    readBytes += read
                                    text.append(
                                        String(chunk.array(), 0, read, StandardCharsets.US_ASCII),
                                    )
                                }
                            }
                        }
                    }
                    assertThat(readBytes).isEqualTo(totalBytes)
                    drain(text.toString())
                }

                val writeWaits: Int = writer.await()
                val drained: Drained = reader.await()

                assertThat(writeWaits).isGreaterThan(0)
                assertThat(drained.events).isEqualTo(JOBS * STEPS)
                assertThat(drained.lastProgressByJob).hasSize(JOBS)
                assertThat(drained.lastProgressByJob.values).allMatch { it == STEPS }
                assertThat(sink.isClosed).isTrue()
                assertThat(source.isClosed).isTrue()
                assertThat(pipe.sink().isOpen).isFalse()
                assertThat(pipe.source().isOpen).isFalse()
            }
        } finally {
            selector.close()
        }
    }

    @Test
    fun closingIdlePipeReleasesWaitingConsumer(): Unit = runBlocking {
        val selector: SelectorManager = SelectorManager(Dispatchers.IO)
        try {
            val pipe: Pipe = selector.provider.openPipe()
            val sink: PipeEnd = PipeEnd(pipe.sink(), selector)
            val source: PipeEnd = PipeEnd(pipe.source(), selector)
            val outcome: CompletableDeferred<String> = CompletableDeferred()

            withTimeout(10_000) {
                // Undispatched start runs the consumer up to its selector suspension before close.
                val consumer = launch(start = CoroutineStart.UNDISPATCHED) {
                    try {
                        source.awaitReady(SelectInterest.READ)
                        outcome.complete("readable")
                    } catch (closed: ClosedChannelCancellationException) {
                        outcome.complete("released on close")
                    }
                }
                assertThat(source.interestedOps and SelectInterest.READ.flag).isNotZero()

                source.close()
                consumer.join()

                assertThat(outcome.await()).isEqualTo("released on close")
                assertThat(source.isClosed).isTrue()
                assertThat(source.interestedOps).isZero()
                assertThat(sink.isClosed).isFalse()
                sink.close()
            }
        } finally {
            selector.close()
        }
    }

    private fun progressEvents(): String {
        val events = StringBuilder()
        for (step in 1..STEPS) {
            for (job in 1..JOBS) {
                events.append("job-").append(job).append(" progress ").append(step).append('\n')
            }
        }
        return events.toString()
    }

    private fun drain(text: String): Drained {
        val lastProgress: MutableMap<String, Int> = LinkedHashMap()
        var events = 0
        for (line in text.lineSequence().filter { it.isNotEmpty() }) {
            val (job: String, _, step: String) = line.split(' ')
            val previous: Int = lastProgress[job] ?: 0
            assertThat(step.toInt()).isEqualTo(previous + 1)
            lastProgress[job] = step.toInt()
            events++
        }
        return Drained(events, lastProgress)
    }

    private companion object {
        const val JOBS: Int = 50
        const val STEPS: Int = 400
    }
}
