/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_compose_runtime.runtime

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.toMutableStateMap
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RuntimeTest {
    @Test
    fun mutationPoliciesControlEquivalentAssignments() {
        val first: Message = Message("same")
        val second: Message = Message("same")

        assertThat(structuralEqualityPolicy<Message>().equivalent(first, second)).isTrue()
        assertThat(referentialEqualityPolicy<Message>().equivalent(first, second)).isFalse()
        assertThat(referentialEqualityPolicy<Message>().equivalent(first, first)).isTrue()
        assertThat(neverEqualPolicy<Message>().equivalent(first, first)).isFalse()

        val structurallyCompared: MutableState<Message> = mutableStateOf(first)
        val referentiallyCompared: MutableState<Message> = mutableStateOf(first, referentialEqualityPolicy())
        structurallyCompared.value = second
        referentiallyCompared.value = second

        assertThat(structurallyCompared.value).isEqualTo(Message("same"))
        assertThat(referentiallyCompared.value).isSameAs(second)
    }

    @Test
    fun derivedStateTracksReadsFromObjectAndPrimitiveState() {
        val label: MutableState<String> = mutableStateOf("cold")
        val count = mutableIntStateOf(1)
        var calculationCount = 0
        val summary = derivedStateOf {
            calculationCount += 1
            "${label.value}:${count.intValue}"
        }

        assertThat(summary.value).isEqualTo("cold:1")
        assertThat(summary.value).isEqualTo("cold:1")
        assertThat(calculationCount).isEqualTo(1)

        count.intValue = 2
        assertThat(summary.value).isEqualTo("cold:2")

        label.value = "warm"
        assertThat(summary.value).isEqualTo("warm:2")
        assertThat(calculationCount).isEqualTo(3)
    }

    @Test
    fun primitiveSnapshotStatesExposeTypedAndBoxedValues() {
        val intState = mutableIntStateOf(7)
        val longState = mutableLongStateOf(11L)
        val floatState = mutableFloatStateOf(1.25f)
        val doubleState = mutableDoubleStateOf(2.5)

        intState.intValue += 5
        longState.longValue *= 3L
        floatState.floatValue += 0.5f
        doubleState.doubleValue /= 2.0

        assertThat(intState.intValue).isEqualTo(12)
        assertThat(intState.value).isEqualTo(12)
        assertThat(longState.longValue).isEqualTo(33L)
        assertThat(longState.value).isEqualTo(33L)
        assertThat(floatState.floatValue).isEqualTo(1.75f)
        assertThat(floatState.value).isEqualTo(1.75f)
        assertThat(doubleState.doubleValue).isEqualTo(1.25)
        assertThat(doubleState.value).isEqualTo(1.25)
    }

    @Test
    fun mutableSnapshotsIsolateChangesUntilApplied() {
        val state: MutableState<String> = mutableStateOf("global")
        val reads = mutableListOf<Any>()
        val writes = mutableListOf<Any>()
        val snapshot = Snapshot.takeMutableSnapshot(
            readObserver = { reads += it },
            writeObserver = { writes += it },
        )

        try {
            snapshot.enter {
                assertThat(state.value).isEqualTo("global")
                state.value = "snapshot"
                assertThat(state.value).isEqualTo("snapshot")
            }

            assertThat(state.value).isEqualTo("global")
            assertThat(reads).isNotEmpty()
            assertThat(writes).isNotEmpty()

            snapshot.apply().check()
            assertThat(state.value).isEqualTo("snapshot")
        } finally {
            snapshot.dispose()
        }
    }

    @Test
    fun stateListSupportsListOperationsAndStableSnapshots() {
        val list = mutableStateListOf("alpha", "gamma")
        list.add(1, "beta")
        val firstSnapshot: List<String> = list.toList()

        list += "delta"
        list.removeRange(1, 3)
        list[1] = "omega"

        assertThat(firstSnapshot).containsExactly("alpha", "beta", "gamma")
        assertThat(list).containsExactly("alpha", "omega")

        val copied = listOf("red", "green", "blue").toMutableStateList()
        copied.removeAll(listOf("green"))
        copied.addAll(listOf("cyan", "magenta"))

        assertThat(copied).containsExactly("red", "blue", "cyan", "magenta")
    }

    @Test
    fun stateMapSupportsMapOperationsAndStableSnapshots() {
        val map = mutableStateMapOf("one" to 1, "two" to 2)
        map["three"] = 3
        val firstSnapshot: Map<String, Int> = map.toMap()

        map.remove("two")
        map["one"] = 10
        map.putAll(mapOf("four" to 4, "five" to 5))

        assertThat(firstSnapshot).containsExactlyInAnyOrderEntriesOf(mapOf("one" to 1, "two" to 2, "three" to 3))
        assertThat(map).containsExactlyInAnyOrderEntriesOf(mapOf("one" to 10, "three" to 3, "four" to 4, "five" to 5))

        val copied = listOf("a" to 1, "b" to 2).toMutableStateMap()
        copied.clear()
        copied["c"] = 3

        assertThat(copied).containsExactlyEntriesOf(mapOf("c" to 3))
    }

    @Test
    fun snapshotFlowEmitsInitialValueAndAppliedChanges() = runBlocking {
        val state: MutableState<String> = mutableStateOf("initial")
        val collected = mutableListOf<String>()
        val collection = launch {
            snapshotFlow { state.value }
                .take(3)
                .toList(collected)
        }

        withTimeout(5_000L) {
            while (collected.isEmpty()) {
                yield()
            }
        }

        Snapshot.withMutableSnapshot {
            state.value = "first"
        }
        Snapshot.withMutableSnapshot {
            state.value = "first"
        }
        Snapshot.withMutableSnapshot {
            state.value = "second"
        }

        withTimeout(5_000L) {
            collection.join()
        }
        assertThat(collected).containsExactly("initial", "first", "second")
    }

    @Test
    fun broadcastFrameClockResumesAwaitersWithFrameTime() = runBlocking {
        val clock = BroadcastFrameClock()
        val frameTimes = mutableListOf<Long>()
        val waiter = launch(clock) {
            frameTimes += withFrameNanos { frameTime -> frameTime }
        }

        withTimeout(5_000L) {
            while (!clock.hasAwaiters) {
                yield()
            }
        }

        clock.sendFrame(123_456_789L)
        withTimeout(5_000L) {
            waiter.join()
        }

        assertThat(clock.hasAwaiters).isFalse()
        assertThat(frameTimes).containsExactly(123_456_789L)
    }

    data class Message(val text: String)
}
