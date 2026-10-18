/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_serialization_json_jvm

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ArraySerializer
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapEntrySerializer
import kotlinx.serialization.builtins.PairSerializer
import kotlinx.serialization.builtins.TripleSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Exercises the built-in serializers for Kotlin standard-library types: `Duration` as ISO-8601,
 * `Uuid`, unsigned integers, `Char`, primitive and object arrays, `Pair`, `Triple`, `Map.Entry`,
 * `Unit`, `object` singletons and `@SerialName` enums, plus the `builtins` factory functions.
 *
 * A job scheduler persists its queue as a JSON snapshot that uses only standard-library value
 * types and reloads it after a restart, rejecting snapshots whose values are out of range.
 */
@OptIn(ExperimentalSerializationApi::class, ExperimentalUuidApi::class)
public class JobSchedulerStdlibBuiltinSerializersTest {

    @Serializable
    public enum class Priority {
        @SerialName("low") LOW,
        @SerialName("normal") NORMAL,
        @SerialName("urgent") URGENT,
    }

    @Serializable
    public sealed interface Trigger

    @Serializable
    @SerialName("manual")
    public data object Manual : Trigger

    @Serializable
    @SerialName("interval")
    public data class Every(val period: Duration) : Trigger

    @Serializable
    public data class Job(
        val id: Uuid,
        val name: String,
        val priority: Priority,
        val trigger: Trigger,
        val timeout: Duration,
        val backoff: List<Duration>,
        val attempts: UByte,
        val fingerprint: ULong,
        val shard: Char,
        val window: Pair<Int, Int>,
        val placement: Triple<String, UInt, Boolean>,
        val payload: ByteArray,
        val weekdays: BooleanArray,
        val owners: Array<String>,
        val lastRun: Duration? = null,
    )

    @Serializable
    public data class Snapshot(val version: UShort, val jobs: List<Job>, val ack: Unit)

    /** Saves and restores the queue as one JSON document. */
    public class SnapshotStore(private val json: Json) {
        public fun save(snapshot: Snapshot): String = json.encodeToString(snapshot)

        public fun load(text: String): Snapshot = json.decodeFromString(text)
    }

    private val json: Json = Json { encodeDefaults = false }

    private val store: SnapshotStore = SnapshotStore(json)

    private val reportId: Uuid = Uuid.parse("6f1c2a4e-9b3d-4c8a-a1f0-0123456789ab")

    private fun nightlyReport(): Job = Job(
        id = reportId,
        name = "nightly-report",
        priority = Priority.URGENT,
        trigger = Every(24.hours),
        timeout = 1.minutes + 30.seconds,
        backoff = listOf(500.milliseconds, 2.seconds, 10.seconds),
        attempts = 200u,
        fingerprint = ULong.MAX_VALUE,
        shard = 'q',
        window = Pair(2, 5),
        placement = Triple("eu-west", 4_000_000_000u, true),
        payload = byteArrayOf(0, 127, -128, 42),
        weekdays = booleanArrayOf(true, false, true, false, true, false, false),
        owners = arrayOf("ops", "finance"),
    )

    private fun cleanup(): Job = Job(
        id = Uuid.parse("00000000-0000-0000-0000-000000000001"),
        name = "cleanup",
        priority = Priority.LOW,
        trigger = Manual,
        timeout = Duration.INFINITE,
        backoff = emptyList(),
        attempts = 0u,
        fingerprint = 0uL,
        shard = '\u00e9',
        window = Pair(0, 0),
        placement = Triple("local", 0u, false),
        payload = ByteArray(0),
        weekdays = BooleanArray(7),
        owners = emptyArray(),
        lastRun = 45.seconds,
    )

    @Test
    fun snapshotEncodesStandardLibraryValuesInTheirWireForms() {
        val text: String = store.save(Snapshot(version = 65_535u, jobs = listOf(nightlyReport()), ack = Unit))
        val root: JsonObject = json.parseToJsonElement(text).jsonObject
        val job: JsonObject = root["jobs"]!!.jsonArray[0].jsonObject

        assertEquals("65535", root["version"]!!.jsonPrimitive.content)
        assertEquals(JsonObject(emptyMap()), root["ack"])
        assertEquals("6f1c2a4e-9b3d-4c8a-a1f0-0123456789ab", job["id"]!!.jsonPrimitive.content)
        assertEquals("urgent", job["priority"]!!.jsonPrimitive.content)
        assertEquals("interval", job["trigger"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("PT24H", job["trigger"]!!.jsonObject["period"]!!.jsonPrimitive.content)
        assertEquals("PT1M30S", job["timeout"]!!.jsonPrimitive.content)
        assertEquals(
            listOf("PT0.500S", "PT2S", "PT10S"),
            job["backoff"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals("200", job["attempts"]!!.jsonPrimitive.content)
        assertEquals("18446744073709551615", job["fingerprint"]!!.jsonPrimitive.content)
        assertEquals("q", job["shard"]!!.jsonPrimitive.content)
        assertEquals("{\"first\":2,\"second\":5}", job["window"].toString())
        assertEquals(
            "{\"first\":\"eu-west\",\"second\":4000000000,\"third\":true}",
            job["placement"].toString(),
        )
        assertEquals(listOf(0L, 127L, -128L, 42L), job["payload"]!!.jsonArray.map { it.jsonPrimitive.long })
        assertEquals(7, job["weekdays"]!!.jsonArray.size)
        assertEquals(listOf("ops", "finance"), job["owners"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertTrue("lastRun" !in job)
    }

    @Test
    fun restartRestoresTheQueueFromItsSnapshot() {
        val text: String = store.save(Snapshot(version = 3u, jobs = listOf(nightlyReport(), cleanup()), ack = Unit))

        val restored: Snapshot = store.load(text)

        assertEquals(3.toUShort(), restored.version)
        val report: Job = restored.jobs[0]
        assertEquals(reportId, report.id)
        assertEquals(Every(1.minutes * 60 * 24), report.trigger)
        assertEquals(90.seconds, report.timeout)
        assertEquals(12_500.milliseconds, report.backoff.fold(Duration.ZERO) { sum, step -> sum + step })
        assertEquals(200.toUByte(), report.attempts)
        assertEquals(ULong.MAX_VALUE, report.fingerprint)
        assertEquals(2 to 5, report.window)
        assertEquals(4_000_000_000u, report.placement.second)
        assertArrayEquals(byteArrayOf(0, 127, -128, 42), report.payload)
        assertEquals(3, report.weekdays.count { it })
        assertArrayEquals(arrayOf("ops", "finance"), report.owners)
        assertNull(report.lastRun)

        val cleanup: Job = restored.jobs[1]
        assertTrue(cleanup.trigger === Manual)
        assertEquals(Priority.LOW, cleanup.priority)
        assertTrue(cleanup.timeout.isInfinite())
        assertEquals('\u00e9', cleanup.shard)
        assertEquals(45.seconds, cleanup.lastRun)
        assertEquals(0, cleanup.payload.size)
    }

    @Test
    fun builtinFactoriesSerializeAdHocStandardLibraryShapes() {
        val pairs: KSerializer<List<Pair<String, Duration>>> =
            ListSerializer(PairSerializer(String.serializer(), Duration.serializer()))
        val slaText: String = json.encodeToString(pairs, listOf("p1" to 5.minutes, "p2" to 1.seconds))
        assertEquals("[{\"first\":\"p1\",\"second\":\"PT5M\"},{\"first\":\"p2\",\"second\":\"PT1S\"}]", slaText)
        assertEquals(listOf("p1" to 5.minutes, "p2" to 1.seconds), json.decodeFromString(pairs, slaText))

        val entry: KSerializer<Map.Entry<Uuid, ULong>> = MapEntrySerializer(Uuid.serializer(), ULong.serializer())
        val lease: Map.Entry<Uuid, ULong> = mapOf(reportId to 9_000_000_000_000_000_000uL).entries.single()
        val leaseText: String = json.encodeToString(entry, lease)
        assertEquals("{\"6f1c2a4e-9b3d-4c8a-a1f0-0123456789ab\":9000000000000000000}", leaseText)
        val decodedLease: Map.Entry<Uuid, ULong> = json.decodeFromString(entry, leaseText)
        assertEquals(reportId, decodedLease.key)
        assertEquals(9_000_000_000_000_000_000uL, decodedLease.value)

        val triple: KSerializer<Triple<Char, UInt, Duration?>> =
            TripleSerializer(Char.serializer(), UInt.serializer(), Duration.serializer().nullable)
        assertEquals("{\"first\":\"x\",\"second\":7,\"third\":null}", json.encodeToString(triple, Triple('x', 7u, null)))

        val blob: ByteArray = json.decodeFromString(ByteArraySerializer(), "[1,2,3]")
        assertArrayEquals(byteArrayOf(1, 2, 3), blob)
        val tags: Array<String> = json.decodeFromString(ArraySerializer(String.serializer()), "[\"a\",\"b\"]")
        assertArrayEquals(arrayOf("a", "b"), tags)

        assertEquals(PrimitiveKind.STRING, Duration.serializer().descriptor.kind)
        assertEquals(PrimitiveKind.STRING, Uuid.serializer().descriptor.kind)
        assertEquals(SerialKind.ENUM, Priority.serializer().descriptor.kind)
        assertEquals("urgent", Priority.serializer().descriptor.getElementName(2))
    }

    @Test
    fun corruptSnapshotValuesAreRejected() {
        val valid: String = store.save(Snapshot(version = 1u, jobs = listOf(cleanup()), ack = Unit))

        assertThrows(SerializationException::class.java) {
            store.load(valid.replace("\"version\":1", "\"version\":70000"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            store.load(valid.replace("\"lastRun\":\"PT45S\"", "\"lastRun\":\"45 seconds\""))
        }
        assertThrows(IllegalArgumentException::class.java) {
            store.load(valid.replace("00000000-0000-0000-0000-000000000001", "not-a-uuid"))
        }
        assertThrows(SerializationException::class.java) {
            store.load(valid.replace("\"priority\":\"low\"", "\"priority\":\"LOW\""))
        }
        assertThrows(SerializationException::class.java) {
            store.load(valid.replace("\"shard\":\"\u00e9\"", "\"shard\":\"ab\""))
        }
    }
}
