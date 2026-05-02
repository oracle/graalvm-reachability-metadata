/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_compose_runtime.runtime

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.PausableMonotonicFrameClock
import androidx.compose.runtime.SnapshotMutationPolicy
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
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.structuralEqualityPolicy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

public class RuntimeTest {
    @Test
    fun mutableStatePoliciesAndPrimitiveStatesUpdateValues() {
        val structuralPolicy = structuralEqualityPolicy<ValueBox>()
        val first = ValueBox("compose")
        val equalButDistinct = ValueBox("compose")
        assertThat(structuralPolicy.equivalent(first, equalButDistinct)).isTrue()

        val referentialPolicy = referentialEqualityPolicy<ValueBox>()
        assertThat(referentialPolicy.equivalent(first, first)).isTrue()
        assertThat(referentialPolicy.equivalent(first, equalButDistinct)).isFalse()

        val neverEqualPolicy = neverEqualPolicy<ValueBox>()
        assertThat(neverEqualPolicy.equivalent(first, first)).isFalse()

        val state = mutableStateOf(first, structuralPolicy)
        val (initialValue, setter) = state
        assertThat(initialValue).isEqualTo(first)
        setter(ValueBox("runtime"))
        assertThat(state.value).isEqualTo(ValueBox("runtime"))

        val intState = mutableIntStateOf(4)
        intState.intValue += 6
        assertThat(intState.intValue).isEqualTo(10)
        assertThat(intState.value).isEqualTo(10)

        val longState = mutableLongStateOf(10L)
        longState.longValue = 42L
        assertThat(longState.longValue).isEqualTo(42L)

        val floatState = mutableFloatStateOf(1.25f)
        floatState.floatValue = 3.75f
        assertThat(floatState.floatValue).isEqualTo(3.75f)

        val doubleState = mutableDoubleStateOf(2.5)
        doubleState.doubleValue *= 2.0
        assertThat(doubleState.doubleValue).isEqualTo(5.0)
    }

    @Test
    fun mutableSnapshotIsolatesChangesUntilApplyAndReportsObservedObjects() {
        val state = mutableStateOf("initial")
        val readObjects = mutableListOf<Any>()
        val writtenObjects = mutableListOf<Any>()
        val snapshot = Snapshot.takeMutableSnapshot(
            { readObject -> readObjects.add(readObject) },
            { writtenObject -> writtenObjects.add(writtenObject) },
        )

        try {
            val valueInsideSnapshot = snapshot.enter {
                val beforeWrite = state.value
                state.value = "changed in snapshot"
                "$beforeWrite -> ${state.value}"
            }

            assertThat(valueInsideSnapshot).isEqualTo("initial -> changed in snapshot")
            assertThat(state.value).isEqualTo("initial")
            assertThat(readObjects).isNotEmpty()
            assertThat(writtenObjects).isNotEmpty()

            snapshot.apply().check()
            assertThat(state.value).isEqualTo("changed in snapshot")
        } finally {
            snapshot.dispose()
        }
    }

    @Test
    fun snapshotCollectionsSupportListAndMapMutationsWithStableViews() {
        val names = mutableStateListOf("alpha", "beta")
        val originalView = names.toList()

        names.add("gamma")
        names[1] = "bravo"
        val removed = names.removeAt(0)

        assertThat(removed).isEqualTo("alpha")
        assertThat(names).containsExactly("bravo", "gamma")
        assertThat(originalView).containsExactly("alpha", "beta")
        assertThat(names.toList()).containsExactly("bravo", "gamma")

        val scores = mutableStateMapOf("one" to 1, "two" to 2)
        val originalMap = scores.toMap()

        scores["three"] = 3
        scores["two"] = 20
        val removedScore = scores.remove("one")

        assertThat(removedScore).isEqualTo(1)
        assertThat(scores).containsEntry("two", 20).containsEntry("three", 3)
        assertThat(scores).doesNotContainKey("one")
        assertThat(originalMap).containsExactlyInAnyOrderEntriesOf(mapOf("one" to 1, "two" to 2))
        assertThat(scores.toMap()).containsExactlyInAnyOrderEntriesOf(mapOf("two" to 20, "three" to 3))
    }

    @Test
    fun derivedStateAndSnapshotFlowTrackSnapshotStateChanges() = runBlocking {
        val counter = mutableIntStateOf(1)
        val doubled = derivedStateOf { counter.intValue * 2 }

        assertThat(doubled.value).isEqualTo(2)
        counter.intValue = 5
        assertThat(doubled.value).isEqualTo(10)

        val emissions = mutableListOf<Int>()
        val firstEmission = CompletableDeferred<Unit>()
        val secondEmission = CompletableDeferred<Unit>()
        val collector = async {
            snapshotFlow { counter.intValue }
                .take(3)
                .collect { value ->
                    emissions.add(value)
                    when (value) {
                        5 -> firstEmission.complete(Unit)
                        6 -> secondEmission.complete(Unit)
                    }
                }
        }

        withTimeout(5_000) { firstEmission.await() }
        counter.intValue = 6
        Snapshot.sendApplyNotifications()
        withTimeout(5_000) { secondEmission.await() }
        counter.intValue = 7
        Snapshot.sendApplyNotifications()

        withTimeout(5_000) { collector.await() }
        assertThat(emissions).containsExactly(5, 6, 7)
    }

    @Test
    fun customSnapshotMutationPolicyMergesConcurrentSnapshotWrites() {
        val counter = mutableStateOf(MergedCounter(0), AdditiveCounterPolicy)
        val firstSnapshot = Snapshot.takeMutableSnapshot()
        val secondSnapshot = Snapshot.takeMutableSnapshot()

        try {
            firstSnapshot.enter {
                counter.value = counter.value.incrementBy(1)
            }
            secondSnapshot.enter {
                counter.value = counter.value.incrementBy(10)
            }

            firstSnapshot.apply().check()
            assertThat(counter.value).isEqualTo(MergedCounter(1))

            secondSnapshot.apply().check()
            assertThat(counter.value).isEqualTo(MergedCounter(11))
        } finally {
            secondSnapshot.dispose()
            firstSnapshot.dispose()
        }
    }

    @Test
    fun snapshotStateObserverInvalidatesOnlyObservedScope() {
        val observed = mutableStateOf("observed")
        val unobserved = mutableStateOf("unobserved")
        val invalidatedScopes = mutableListOf<String>()
        val observer = SnapshotStateObserver { callback -> callback() }

        observer.start()
        try {
            observer.observeReads("observed-scope", { scope -> invalidatedScopes.add(scope) }) {
                assertThat(observed.value).isEqualTo("observed")
            }

            Snapshot.withMutableSnapshot {
                unobserved.value = "ignored"
            }
            Snapshot.sendApplyNotifications()
            assertThat(invalidatedScopes).isEmpty()

            Snapshot.withMutableSnapshot {
                observed.value = "changed"
            }
            Snapshot.sendApplyNotifications()
            assertThat(invalidatedScopes).containsExactly("observed-scope")
        } finally {
            observer.stop()
            observer.clear()
        }
    }

    @Test
    fun broadcastFrameClockResumesAwaitersWithFrameTime() = runBlocking {
        val clock = BroadcastFrameClock()
        val frameResult = async {
            clock.withFrameNanos { frameTimeNanos ->
                "frame=$frameTimeNanos"
            }
        }

        yieldUntil { clock.hasAwaiters }
        clock.sendFrame(123_456_789L)

        assertThat(withTimeout(5_000) { frameResult.await() }).isEqualTo("frame=123456789")
        assertThat(clock.hasAwaiters).isFalse()
    }

    @Test
    fun pausableFrameClockDefersAwaitersUntilResumed() = runBlocking {
        val delegateClock = BroadcastFrameClock()
        val pausableClock = PausableMonotonicFrameClock(delegateClock)
        pausableClock.pause()

        val frameResult = async {
            pausableClock.withFrameNanos { frameTimeNanos -> frameTimeNanos / 1_000_000L }
        }

        repeat(5) { yield() }
        assertThat(pausableClock.isPaused).isTrue()
        assertThat(delegateClock.hasAwaiters).isFalse()

        pausableClock.resume()
        yieldUntil { delegateClock.hasAwaiters }
        delegateClock.sendFrame(16_000_000L)

        assertThat(withTimeout(5_000) { frameResult.await() }).isEqualTo(16L)
        assertThat(pausableClock.isPaused).isFalse()
    }

    private suspend fun yieldUntil(condition: () -> Boolean) {
        withTimeout(5_000) {
            while (!condition()) {
                yield()
            }
        }
    }

    private data class MergedCounter(val count: Int) {
        fun incrementBy(delta: Int): MergedCounter = copy(count = count + delta)
    }

    private object AdditiveCounterPolicy : SnapshotMutationPolicy<MergedCounter> {
        override fun equivalent(a: MergedCounter, b: MergedCounter): Boolean = a == b

        override fun merge(
            previous: MergedCounter,
            current: MergedCounter,
            applied: MergedCounter,
        ): MergedCounter = MergedCounter(current.count + applied.count - previous.count)
    }

    private data class ValueBox(val value: String)
}
