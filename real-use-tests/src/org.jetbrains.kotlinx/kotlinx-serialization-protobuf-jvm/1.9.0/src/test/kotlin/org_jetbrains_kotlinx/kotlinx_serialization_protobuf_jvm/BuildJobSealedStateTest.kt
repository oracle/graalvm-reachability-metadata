/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_serialization_protobuf_jvm

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Closed polymorphism over a sealed class hierarchy with `object` cases, encoded by `ProtoBuf` without a serializers module.
 *
 * A CI server persists the state machine of every build job as protobuf records. Job states form
 * a sealed hierarchy: stateless states are Kotlin `object`s, the others carry data, and terminal
 * states are grouped under a nested sealed class. The server relies on the compiler-generated
 * sealed serializer alone: it checks that a job history round-trips with singleton identity kept,
 * that each state travels as its serial name plus a nested message, that a nullable sealed field
 * stays absent when unset, and that a state name from a newer server is refused.
 */
@OptIn(ExperimentalSerializationApi::class)
class BuildJobSealedStateTest {

    @Serializable
    sealed class JobState {

        @Serializable
        @SerialName("queued")
        data object Queued : JobState()

        @Serializable
        @SerialName("running")
        data class Running(
            @ProtoNumber(1) val agent: String,
            @ProtoNumber(2) val startedAtMillis: Long,
        ) : JobState()

        @Serializable
        sealed class Finished : JobState() {
            abstract val durationMillis: Long
        }

        @Serializable
        @SerialName("succeeded")
        data class Succeeded(
            @ProtoNumber(1) override val durationMillis: Long,
            @ProtoNumber(2) val artifacts: List<String> = emptyList(),
        ) : Finished()

        @Serializable
        @SerialName("failed")
        data class Failed(
            @ProtoNumber(1) override val durationMillis: Long,
            @ProtoNumber(2) val exitCode: Int,
            @ProtoNumber(3) val failedStep: String,
        ) : Finished()

        @Serializable
        @SerialName("cancelled")
        data object Cancelled : JobState()
    }

    @Serializable
    data class Transition(
        @ProtoNumber(1) val sequence: Int,
        @ProtoNumber(2) val state: JobState,
    )

    @Serializable
    data class BuildJob(
        @ProtoNumber(1) val jobId: String,
        @ProtoNumber(2) val pipeline: String,
        @ProtoNumber(3) val history: List<Transition>,
        @ProtoNumber(4) val lastFinished: JobState.Finished? = null,
    )

    /** The type-tagged message the format puts on the wire for every [JobState]. */
    @Serializable
    class TaggedState(
        @ProtoNumber(1) val type: String,
        @ProtoNumber(2) val value: ByteArray = ByteArray(0),
    )

    private val jobStore: ProtoBuf = ProtoBuf

    @Test
    fun roundTripsJobHistoryAndKeepsObjectStatesSingletons() {
        val job = BuildJob(
            jobId = "job-4711",
            pipeline = "release",
            history = listOf(
                Transition(1, JobState.Queued),
                Transition(2, JobState.Running("agent-linux-3", 1_700_000_000_000)),
                Transition(3, JobState.Failed(42_000, 2, "integration-tests")),
                Transition(4, JobState.Queued),
                Transition(5, JobState.Running("agent-linux-1", 1_700_000_090_000)),
                Transition(6, JobState.Succeeded(61_500, listOf("app.jar", "app.sbom.json"))),
            ),
            lastFinished = JobState.Succeeded(61_500, listOf("app.jar", "app.sbom.json")),
        )

        val bytes: ByteArray = jobStore.encodeToByteArray(job)
        val restored: BuildJob = jobStore.decodeFromByteArray(bytes)

        assertEquals(job, restored)
        assertSame(JobState.Queued, restored.history[0].state)
        assertSame(JobState.Queued, restored.history[3].state)
        assertEquals(listOf("queued", "running", "failed", "queued", "running", "succeeded"), restored.history.map { label(it.state) })
        val finished: List<JobState.Finished> = restored.history.map { it.state }.filterIsInstance<JobState.Finished>()
        assertEquals(103_500L, finished.sumOf { it.durationMillis })
    }

    @Test
    fun encodesEachStateAsSerialNameAndNestedMessage() {
        val failed: JobState = JobState.Failed(9_000, 137, "docker-build")

        val failedBytes: ByteArray = jobStore.encodeToByteArray(failed)
        val failedTag: TaggedState = jobStore.decodeFromByteArray(failedBytes)

        assertEquals("failed", failedTag.type)
        assertEquals(failed, jobStore.decodeFromByteArray<JobState.Failed>(failedTag.value))
        assertEquals(failed, jobStore.decodeFromByteArray<JobState>(failedBytes))

        val cancelledBytes: ByteArray = jobStore.encodeToByteArray<JobState>(JobState.Cancelled)
        val cancelledTag: TaggedState = jobStore.decodeFromByteArray(cancelledBytes)

        assertEquals("cancelled", cancelledTag.type)
        assertEquals(0, cancelledTag.value.size)
        assertSame(JobState.Cancelled, jobStore.decodeFromByteArray<JobState>(cancelledBytes))
    }

    @Test
    fun leavesUnsetNullableSealedFieldAbsent() {
        val pending = BuildJob("job-5000", "nightly", listOf(Transition(1, JobState.Queued)))
        val cancelled = pending.copy(
            history = pending.history + Transition(2, JobState.Cancelled),
            lastFinished = null,
        )

        val pendingBytes: ByteArray = jobStore.encodeToByteArray(pending)
        val restored: BuildJob = jobStore.decodeFromByteArray(jobStore.encodeToByteArray(cancelled))

        assertNull(jobStore.decodeFromByteArray<BuildJob>(pendingBytes).lastFinished)
        assertNull(restored.lastFinished)
        assertSame(JobState.Cancelled, restored.history.last().state)

        val failedJob: BuildJob = pending.copy(lastFinished = JobState.Failed(500, 1, "lint"))
        val failedBytes: ByteArray = jobStore.encodeToByteArray(failedJob)
        assertTrue(failedBytes.size > pendingBytes.size)
        assertEquals(failedJob, jobStore.decodeFromByteArray<BuildJob>(failedBytes))
    }

    @Test
    fun refusesStateNamesOutsideTheSealedHierarchy() {
        val pausedPayload: ByteArray = jobStore.encodeToByteArray(JobState.Running("agent-mac-2", 1))
        val fromNewerServer: ByteArray = jobStore.encodeToByteArray(
            TransitionOnTheWire(7, TaggedState("paused", pausedPayload)),
        )

        val failure: SerializationException = assertThrows {
            jobStore.decodeFromByteArray<Transition>(fromNewerServer)
        }
        assertTrue(failure.message!!.contains("paused"), failure.message)
    }

    /** A [Transition] as a server that knows an extra `paused` state writes it. */
    @Serializable
    class TransitionOnTheWire(
        @ProtoNumber(1) val sequence: Int,
        @ProtoNumber(2) val state: TaggedState,
    )

    private fun label(state: JobState): String = when (state) {
        JobState.Queued -> "queued"
        is JobState.Running -> "running"
        is JobState.Succeeded -> "succeeded"
        is JobState.Failed -> "failed"
        JobState.Cancelled -> "cancelled"
    }
}
