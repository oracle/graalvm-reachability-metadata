/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_serialization_cbor_jvm

import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Exercises CBOR encoding of Kotlin built-in value types: unsigned integers, primitive
 * arrays, `Pair` and `Triple`, `kotlin.time.Duration` and kotlinx-datetime local types.
 *
 * A maintenance scheduler keeps its job definitions and run history as CBOR files on
 * disk. Jobs carry unsigned checksums and counters, timeouts and backoff steps as
 * durations, wall-clock start times, and shard ranges; the history keeps primitive
 * arrays of measurements. The scheduler also reads load reports written by a small
 * agent that emits half-precision CBOR floats.
 */
@OptIn(ExperimentalSerializationApi::class)
public class JobScheduleBuiltinTypesCborTest {

    @Serializable
    public enum class Weekday { MON, TUE, WED, THU, FRI, SAT, SUN }

    @Serializable
    public data class MaintenanceJob(
        val name: String,
        val priority: UByte,
        val maxAttempts: UShort,
        val runId: UInt,
        val checksum: ULong,
        val firstRun: LocalDateTime,
        val dailyAt: LocalTime,
        val timeout: Duration,
        val backoff: List<Duration>,
        val activeDays: Set<Weekday>,
        val shardRange: Pair<Int, Int>,
        val schemaVersion: Triple<Int, Int, Int>,
        val owner: String? = null,
        val labels: Map<String, String?> = emptyMap(),
    )

    @Serializable
    public class RunHistory(
        val jobName: String,
        val durationsMillis: LongArray,
        val exitCodes: IntArray,
        val succeeded: BooleanArray,
        val cpuLoad: FloatArray,
        val grades: CharArray,
    ) {
        public fun successRate(): Double = succeeded.count { ok: Boolean -> ok }.toDouble() / succeeded.size
    }

    /** What an operator's inspection tool sees: the wire form of the scheduling fields. */
    @Serializable
    public class JobWireView(
        val name: String,
        val timeout: String,
        val backoff: List<String>,
        val firstRun: String,
        val dailyAt: String,
        val shardRange: Map<String, Int>,
    )

    @Serializable
    public data class LoadReport(val host: String, val load: Float, val memoryRatio: Float)

    /** Stores scheduler state as one CBOR file per document. */
    public class ScheduleStore(private val directory: Path, private val cbor: Cbor) {

        public fun saveJob(job: MaintenanceJob): Path {
            val file: Path = directory.resolve("${job.name}.job.cbor")
            Files.write(file, cbor.encodeToByteArray(job))
            return file
        }

        public fun loadJob(name: String): MaintenanceJob =
            cbor.decodeFromByteArray<MaintenanceJob>(Files.readAllBytes(directory.resolve("$name.job.cbor")))

        public fun saveHistory(history: RunHistory): Unit {
            Files.write(directory.resolve("${history.jobName}.history.cbor"), cbor.encodeToByteArray(history))
        }

        public fun loadHistory(jobName: String): RunHistory =
            cbor.decodeFromByteArray<RunHistory>(Files.readAllBytes(directory.resolve("$jobName.history.cbor")))
    }

    private val schedulerCbor: Cbor = Cbor { useDefiniteLengthEncoding = true }

    private val inspectorCbor: Cbor = Cbor { ignoreUnknownKeys = true }

    private fun vacuumJob(): MaintenanceJob = MaintenanceJob(
        name = "vacuum-orders",
        priority = 200u,
        maxAttempts = 60_000u,
        runId = 4_000_000_000u,
        checksum = ULong.MAX_VALUE,
        firstRun = LocalDateTime(2026, 10, 17, 2, 30),
        dailyAt = LocalTime(2, 30),
        timeout = 1.hours + 30.minutes,
        backoff = listOf(30.seconds, 2.minutes, 1500.milliseconds),
        activeDays = linkedSetOf(Weekday.SAT, Weekday.SUN),
        shardRange = 0 to 63,
        schemaVersion = Triple(3, 1, 4),
        labels = mapOf("team" to "storage", "ticket" to null),
    )

    @Test
    fun jobDefinitionSurvivesAFileRoundTripWithItsBuiltinTypes(@TempDir directory: Path): Unit {
        val store: ScheduleStore = ScheduleStore(directory, schedulerCbor)
        val job: MaintenanceJob = vacuumJob()

        val file: Path = store.saveJob(job)
        val reloaded: MaintenanceJob = store.loadJob("vacuum-orders")

        assertEquals(job, reloaded)
        assertEquals(200u.toUByte(), reloaded.priority)
        assertEquals(60_000u.toUShort(), reloaded.maxAttempts)
        assertEquals(4_000_000_000u, reloaded.runId)
        assertEquals(ULong.MAX_VALUE, reloaded.checksum)
        assertEquals(90.minutes, reloaded.timeout)
        assertEquals(listOf(Weekday.SAT, Weekday.SUN), reloaded.activeDays.toList())
        assertEquals(64, reloaded.shardRange.second - reloaded.shardRange.first + 1)
        assertNull(reloaded.owner)
        assertNull(reloaded.labels.getValue("ticket"))
        // A definite-length map of 13 entries: the default `owner` is left out of the file.
        assertEquals(0xA0 + 13, Files.readAllBytes(file)[0].toInt() and 0xFF)
    }

    @Test
    fun inspectionToolSeesIsoDurationsAndDateTimesOnTheWire(): Unit {
        val encoded: ByteArray = schedulerCbor.encodeToByteArray(vacuumJob())

        val view: JobWireView = inspectorCbor.decodeFromByteArray<JobWireView>(encoded)

        assertEquals("vacuum-orders", view.name)
        assertEquals("PT1H30M", view.timeout)
        assertEquals(listOf("PT30S", "PT2M", "PT1.500S"), view.backoff)
        assertEquals("2026-10-17T02:30", view.firstRun)
        assertEquals("02:30", view.dailyAt)
        assertEquals(mapOf("first" to 0, "second" to 63), view.shardRange)
    }

    @Test
    fun runHistoryKeepsItsPrimitiveArrays(@TempDir directory: Path): Unit {
        val store: ScheduleStore = ScheduleStore(directory, schedulerCbor)
        val history: RunHistory = RunHistory(
            jobName = "vacuum-orders",
            durationsMillis = longArrayOf(81_250L, 79_900L, 3_600_000L, 80_010L),
            exitCodes = intArrayOf(0, 0, 137, 0),
            succeeded = booleanArrayOf(true, true, false, true),
            cpuLoad = floatArrayOf(0.25f, 0.5f, 1.0f, 0.375f),
            grades = charArrayOf('A', 'A', 'F', 'B'),
        )

        store.saveHistory(history)
        val reloaded: RunHistory = store.loadHistory("vacuum-orders")

        assertArrayEquals(history.durationsMillis, reloaded.durationsMillis)
        assertArrayEquals(history.exitCodes, reloaded.exitCodes)
        assertArrayEquals(history.succeeded, reloaded.succeeded)
        assertArrayEquals(history.cpuLoad, reloaded.cpuLoad)
        assertArrayEquals(history.grades, reloaded.grades)
        assertEquals(0.75, reloaded.successRate())
        assertEquals(3_600_000L, reloaded.durationsMillis.max())
    }

    @Test
    fun schedulerReadsHalfPrecisionLoadReportsFromAnAgent(): Unit {
        // {"host": "db-1", "load": 1.5 (f16), "memoryRatio": 0.25 (f16)} as a constrained agent writes it.
        val agentReport: ByteArray = byteArrayOf(
            0xA3.toByte(),
            0x64, 'h'.code.toByte(), 'o'.code.toByte(), 's'.code.toByte(), 't'.code.toByte(),
            0x64, 'd'.code.toByte(), 'b'.code.toByte(), '-'.code.toByte(), '1'.code.toByte(),
            0x64, 'l'.code.toByte(), 'o'.code.toByte(), 'a'.code.toByte(), 'd'.code.toByte(),
            0xF9.toByte(), 0x3E, 0x00,
            0x6B, 'm'.code.toByte(), 'e'.code.toByte(), 'm'.code.toByte(), 'o'.code.toByte(),
            'r'.code.toByte(), 'y'.code.toByte(), 'R'.code.toByte(), 'a'.code.toByte(),
            't'.code.toByte(), 'i'.code.toByte(), 'o'.code.toByte(),
            0xF9.toByte(), 0x34, 0x00,
        )

        val report: LoadReport = schedulerCbor.decodeFromByteArray<LoadReport>(agentReport)

        assertEquals(LoadReport("db-1", 1.5f, 0.25f), report)
        val reencoded: ByteArray = schedulerCbor.encodeToByteArray(report)
        assertEquals(report, schedulerCbor.decodeFromByteArray<LoadReport>(reencoded))
    }
}
