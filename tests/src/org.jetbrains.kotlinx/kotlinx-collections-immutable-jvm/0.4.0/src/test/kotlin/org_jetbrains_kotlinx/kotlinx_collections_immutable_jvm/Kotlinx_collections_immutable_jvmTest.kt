/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_collections_immutable_jvm

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.intersect
import kotlinx.collections.immutable.minus
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentHashMapOf
import kotlinx.collections.immutable.persistentHashSetOf
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.plus
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.collections.immutable.toPersistentHashMap
import kotlinx.collections.immutable.toPersistentHashSet
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.collections.immutable.toPersistentSet
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

public class Kotlinx_collections_immutable_jvmTest {
    @Test
    fun persistentListKeepsPreviousVersionsAcrossIndexedUpdatesAndBuilderSnapshots(): Unit {
        val original: PersistentList<Int> = (0 until 40).toPersistentList()
        val expected: MutableList<Int> = (0 until 40).toMutableList()

        val edited: PersistentList<Int> = original
            .add(0, -1)
            .also { expected.add(0, -1) }
            .add(20, 200)
            .also { expected.add(20, 200) }
            .set(3, 33)
            .also { expected[3] = 33 }
            .removeAt(5)
            .also { expected.removeAt(5) }
            .remove(39)
            .also { expected.remove(39) }
            .addAll(listOf(41, 42))
            .also { expected.addAll(listOf(41, 42)) }

        assertThat(original).containsExactlyElementsOf(0 until 40)
        assertThat(edited).containsExactlyElementsOf(expected)
        assertThat(edited.subList(18, 23)).containsExactlyElementsOf(expected.subList(18, 23))
        assertThat(edited.listIterator(edited.size).asReversedList()).containsExactlyElementsOf(expected.asReversed())

        val builder: PersistentList.Builder<Int> = edited.builder()
        val beforeBuilderChanges: PersistentList<Int> = builder.build()
        val builderExpected: MutableList<Int> = expected.toMutableList()

        builder.add(1, -100)
        builderExpected.add(1, -100)
        builder.removeAll { it % 10 == 0 }
        builderExpected.removeAll { it % 10 == 0 }
        builder[2] = 222
        builderExpected[2] = 222

        assertThat(beforeBuilderChanges).containsExactlyElementsOf(edited)
        assertThat(builder.build()).containsExactlyElementsOf(builderExpected)
        assertThat(edited).containsExactlyElementsOf(expected)
    }

    @Test
    fun persistentListBuilderMutableIteratorSupportsInPlaceTraversalEdits(): Unit {
        val original: PersistentList<String> = persistentListOf("red", "green", "blue", "yellow")
        val builder: PersistentList.Builder<String> = original.builder()
        val iterator: MutableListIterator<String> = builder.listIterator()

        while (iterator.hasNext()) {
            when (iterator.next()) {
                "red" -> iterator.set("crimson")
                "green" -> iterator.add("lime")
                "blue" -> iterator.remove()
            }
        }

        assertThat(original).containsExactly("red", "green", "blue", "yellow")
        assertThat(builder.build()).containsExactly("crimson", "green", "lime", "yellow")

        val reverseIterator: MutableListIterator<String> = builder.listIterator(builder.size)
        while (reverseIterator.hasPrevious()) {
            if (reverseIterator.previous() == "yellow") {
                reverseIterator.set("amber")
            }
        }

        assertThat(builder.build()).containsExactly("crimson", "green", "lime", "amber")
    }

    @Test
    fun persistentListFactoriesConversionsOperatorsAndMutateProduceExpectedLists(): Unit {
        val fromVararg: PersistentList<String> = persistentListOf("alpha", "beta")
        val extended: PersistentList<String> = fromVararg + arrayOf("gamma", "delta") + sequenceOf("epsilon")
        val shortened: PersistentList<String> = extended - sequenceOf("beta", "missing") - arrayOf("delta")
        val mutated: PersistentList<String> = shortened.mutate { mutableList ->
            mutableList.add(1, "inserted")
            mutableList.remove("alpha")
            mutableList.add("omega")
        }

        assertThat(fromVararg).containsExactly("alpha", "beta")
        assertThat(extended).containsExactly("alpha", "beta", "gamma", "delta", "epsilon")
        assertThat(shortened).containsExactly("alpha", "gamma", "epsilon")
        assertThat(mutated).containsExactly("inserted", "gamma", "epsilon", "omega")
        assertThat("kotlin".toPersistentList()).containsExactly('k', 'o', 't', 'l', 'i', 'n')
        assertThat(listOf(1, 2, 3).toImmutableList()).containsExactly(1, 2, 3)
        assertThat(arrayOf("x", "y").toPersistentList()).containsExactly("x", "y")
        assertThat(sequenceOf(4, 5, 6).toPersistentList()).containsExactly(4, 5, 6)
    }

    @Test
    fun orderedPersistentSetHandlesUniquenessCollisionsAndBuilderMutations(): Unit {
        val one: CollidingValue = CollidingValue(1)
        val duplicateOne: CollidingValue = CollidingValue(1)
        val two: CollidingValue = CollidingValue(2)
        val three: CollidingValue = CollidingValue(3)
        val four: CollidingValue = CollidingValue(4)
        val five: CollidingValue = CollidingValue(5)

        val original: PersistentSet<CollidingValue> = persistentSetOf(one, two, duplicateOne, three)
        val changed: PersistentSet<CollidingValue> = original
            .add(four)
            .remove(two)
            .add(one)
        val retained: PersistentSet<CollidingValue> = changed.retainAll(listOf(three, four, five))
        val intersected: PersistentSet<CollidingValue> = changed.intersect(listOf(five, four, one))

        assertThat(original).containsExactly(one, two, three)
        assertThat(changed).containsExactly(one, three, four)
        assertThat(retained).containsExactly(three, four)
        assertThat(intersected).containsExactly(one, four)
        assertThat(original).containsExactly(one, two, three)

        val builder: PersistentSet.Builder<CollidingValue> = original.builder()
        val beforeBuilderChanges: PersistentSet<CollidingValue> = builder.build()
        builder.remove(one)
        builder.add(four)
        builder.add(five)
        builder.removeAll { it.id % 2 == 0 }

        assertThat(beforeBuilderChanges).containsExactly(one, two, three)
        assertThat(builder.build()).containsExactly(three, five)
    }

    @Test
    fun hashPersistentSetSupportsHashTrieOperationsAndConversions(): Unit {
        val values: List<CollidingValue> = (0 until 12).map(::CollidingValue)
        val original: PersistentSet<CollidingValue> = values.toPersistentHashSet()
        val filtered: PersistentSet<CollidingValue> = original.removeAll { it.id < 4 }.add(CollidingValue(99))
        val fromSequence: PersistentSet<CollidingValue> = sequenceOf(values[1], values[2], values[1]).toPersistentHashSet()

        assertThat(original).containsExactlyInAnyOrderElementsOf(values)
        assertThat(filtered).containsExactlyInAnyOrderElementsOf(values.drop(4) + CollidingValue(99))
        assertThat(fromSequence).containsExactlyInAnyOrder(values[1], values[2])
        assertThat(persistentHashSetOf(values[0], values[0], values[3])).containsExactlyInAnyOrder(values[0], values[3])
        assertThat("banana".toPersistentSet()).containsExactly('b', 'a', 'n')
        assertThat(arrayOf("a", "b", "a").toImmutableSet()).containsExactly("a", "b")
    }

    @Test
    fun orderedPersistentMapPreservesSnapshotsWhileUpdatingRemovingAndUsingBuilder(): Unit {
        val one: CollidingValue = CollidingValue(1)
        val duplicateOne: CollidingValue = CollidingValue(1)
        val two: CollidingValue = CollidingValue(2)
        val three: CollidingValue = CollidingValue(3)
        val four: CollidingValue = CollidingValue(4)
        val five: CollidingValue = CollidingValue(5)

        val original: PersistentMap<CollidingValue, String> = persistentMapOf(
            one to "one",
            two to "two",
            duplicateOne to "uno",
        )
        val updated: PersistentMap<CollidingValue, String> = original
            .put(three, "three")
            .put(two, "dos")
            .remove(one, "not-current-value")
            .remove(one)
        val withOperators: PersistentMap<CollidingValue, String> = updated + sequenceOf(
            four to "four",
            three to "tres",
        )

        assertThat(original).containsEntry(one, "uno").containsEntry(two, "two").hasSize(2)
        assertThat(original.keys).containsExactly(one, two)
        assertThat(updated).containsEntry(two, "dos").containsEntry(three, "three").hasSize(2)
        assertThat(updated.keys).containsExactly(two, three)
        assertThat(withOperators).containsEntry(two, "dos").containsEntry(three, "tres").containsEntry(four, "four")
        assertThat(withOperators.keys).containsExactly(two, three, four)

        val builder: PersistentMap.Builder<CollidingValue, String> = withOperators.builder()
        val beforeBuilderChanges: PersistentMap<CollidingValue, String> = builder.build()
        builder[five] = "five"
        builder[three] = "THREE"
        builder.remove(two)

        assertThat(beforeBuilderChanges).isEqualTo(withOperators)
        assertThat(builder.build())
            .containsEntry(three, "THREE")
            .containsEntry(four, "four")
            .containsEntry(five, "five")
            .doesNotContainKey(two)
    }

    @Test
    fun persistentMapFactoriesConversionsOperatorsAndMutateProduceExpectedMaps(): Unit {
        val base: PersistentMap<String, Int> = mapOf("a" to 1, "b" to 2).toPersistentMap()
        val augmented: PersistentMap<String, Int> = base + arrayOf("c" to 3, "b" to 20)
        val removed: PersistentMap<String, Int> = augmented - sequenceOf("a", "missing")
        val mutated: PersistentMap<String, Int> = removed.mutate { mutableMap ->
            mutableMap["d"] = 4
            mutableMap.remove("c")
            mutableMap["b"] = 200
        }

        assertThat(base).containsEntry("a", 1).containsEntry("b", 2).hasSize(2)
        assertThat(augmented).containsEntry("a", 1).containsEntry("b", 20).containsEntry("c", 3).hasSize(3)
        assertThat(removed).containsEntry("b", 20).containsEntry("c", 3).hasSize(2)
        assertThat(mutated).containsEntry("b", 200).containsEntry("d", 4).hasSize(2)
        assertThat(persistentHashMapOf("x" to 1, "x" to 2, "y" to 3)).containsEntry("x", 2).containsEntry("y", 3).hasSize(2)
        assertThat(mapOf("left" to 10).toImmutableMap()).containsEntry("left", 10)
        assertThat(mapOf("right" to 20).toPersistentHashMap()).containsEntry("right", 20)
    }

    @Test
    fun hashPersistentMapBuilderViewsSupportEntryUpdatesAndRemovals(): Unit {
        val one: CollidingValue = CollidingValue(1)
        val two: CollidingValue = CollidingValue(2)
        val three: CollidingValue = CollidingValue(3)
        val four: CollidingValue = CollidingValue(4)
        val five: CollidingValue = CollidingValue(5)
        val original: PersistentMap<CollidingValue, String> = persistentHashMapOf(
            one to "one",
            two to "two",
            three to "three",
            four to "four",
        )

        val builder: PersistentMap.Builder<CollidingValue, String> = original.builder()
        val beforeViewChanges: PersistentMap<CollidingValue, String> = builder.build()
        val updatedEntry: MutableMap.MutableEntry<CollidingValue, String> = builder.entries.single { it.key == two }
        val oldValue: String = updatedEntry.setValue("dos")

        builder.entries.remove(builder.entries.single { it.key == three })
        builder.keys.remove(one)
        builder.values.remove("four")
        builder[five] = "five"

        assertThat(oldValue).isEqualTo("two")
        assertThat(beforeViewChanges).isEqualTo(original)
        assertThat(original)
            .containsEntry(one, "one")
            .containsEntry(two, "two")
            .containsEntry(three, "three")
            .containsEntry(four, "four")
            .hasSize(4)
        assertThat(builder.build())
            .containsEntry(two, "dos")
            .containsEntry(five, "five")
            .doesNotContainKey(one)
            .doesNotContainKey(three)
            .doesNotContainKey(four)
            .hasSize(2)
    }

    private fun <E> ListIterator<E>.asReversedList(): List<E> {
        val reversed: MutableList<E> = mutableListOf()
        while (hasPrevious()) {
            reversed.add(previous())
        }
        return reversed
    }

    private data class CollidingValue(val id: Int) {
        override fun hashCode(): Int = 7
    }
}
