/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_stdlib_jdk8

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration as JavaDuration
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.Collectors
import java.util.stream.DoubleStream
import java.util.stream.IntStream
import java.util.stream.LongStream
import java.util.stream.Stream
import kotlin.random.Random
import kotlin.streams.asSequence
import kotlin.streams.asStream
import kotlin.streams.toList
import kotlin.time.ExperimentalTime
import kotlin.time.toJavaDuration
import kotlin.time.toKotlinDuration

public class Kotlin_stdlib_jdk8Test {
    @Test
    fun objectStreamAsSequenceIsLazyAndKeepsEncounterOrder() {
        val closeCount = AtomicInteger(0)
        val mappingCount = AtomicInteger(0)
        val stream = Stream.of("alpha", "beta", "gamma")
            .onClose { closeCount.incrementAndGet() }
            .map { value ->
                mappingCount.incrementAndGet()
                value.uppercase()
            }

        try {
            val iterator = stream.asSequence().iterator()

            assertThat(mappingCount.get()).isZero()
            assertThat(iterator.next()).isEqualTo("ALPHA")
            assertThat(mappingCount.get()).isEqualTo(1)
            assertThat(iterator.asSequence().toList()).containsExactly("BETA", "GAMMA")
            assertThat(mappingCount.get()).isEqualTo(3)
        } finally {
            stream.close()
        }

        assertThat(closeCount.get()).isEqualTo(1)
    }

    @Test
    fun primitiveStreamsAsSequencesSupportKotlinSequenceOperations() {
        val evenSquares = IntStream.rangeClosed(1, 6)
            .asSequence()
            .filter { value -> value % 2 == 0 }
            .map { value -> value * value }
            .toList()
        assertThat(evenSquares).containsExactly(4, 16, 36)

        val longValues = LongStream.of(4L, 8L, 15L, 16L, 23L, 42L)
            .asSequence()
            .drop(2)
            .take(3)
            .toList()
        assertThat(longValues).containsExactly(15L, 16L, 23L)

        val normalizedDoubles = DoubleStream.of(1.5, 3.0, 4.5)
            .asSequence()
            .map { value -> value / 1.5 }
            .toList()
        assertThat(normalizedDoubles).containsExactly(1.0, 2.0, 3.0)
    }

    @Test
    fun sequenceAsStreamSupportsLazyJavaStreamPipelines() {
        val producedCount = AtomicInteger(0)
        val source = sequence {
            for (value in 1..10) {
                producedCount.incrementAndGet()
                yield(value)
            }
        }
        val stream = source.asStream()

        val firstThreeOddLabels = try {
            stream
                .filter { value -> value % 2 == 1 }
                .map { value -> "value-$value" }
                .limit(3)
                .collect(Collectors.toList())
        } finally {
            stream.close()
        }

        assertThat(firstThreeOddLabels).containsExactly("value-1", "value-3", "value-5")
        assertThat(producedCount.get()).isEqualTo(5)
    }

    @Test
    fun primitiveStreamToListExtensionsPreserveBoxedValuesAndOrder() {
        val ints = IntStream.of(9, 5, 9, 1).toList()
        val longs = LongStream.of(Long.MIN_VALUE, 0L, Long.MAX_VALUE).toList()
        val doubles = DoubleStream.of(-0.0, 0.5, 2.25).toList()

        assertThat(ints).containsExactly(9, 5, 9, 1)
        assertThat(longs).containsExactly(Long.MIN_VALUE, 0L, Long.MAX_VALUE)
        assertThat(doubles).containsExactly(-0.0, 0.5, 2.25)
    }

    @Test
    fun objectStreamToListExtensionPreservesEncounterOrderAndNullElements() {
        val values: List<String?> = Stream.of("north", null, "south", "north").toList()

        assertThat(values).containsExactly("north", null, "south", "north")
    }

    @Test
    fun namedRegexGroupsExposeJdkMatcherGroupsByName() {
        val regex = Regex("""(?<scheme>[a-z]+)://(?<host>[^/:]+)(?::(?<port>\d+))?/(?<path>.+)""")
        val matchWithPort = regex.matchEntire("https://kotlinlang.org:443/docs/home")
        val matchWithoutPort = regex.matchEntire("https://kotlinlang.org/docs/home")

        requireNotNull(matchWithPort)
        requireNotNull(matchWithoutPort)

        assertThat(matchWithPort.groups["scheme"]?.value).isEqualTo("https")
        assertThat(matchWithPort.groups["host"]?.value).isEqualTo("kotlinlang.org")
        assertThat(matchWithPort.groups["port"]?.value).isEqualTo("443")
        assertThat(matchWithPort.groups["path"]?.value).isEqualTo("docs/home")

        val hostGroup = requireNotNull(matchWithPort.groups["host"])
        assertThat("https://kotlinlang.org:443/docs/home".substring(hostGroup.range)).isEqualTo("kotlinlang.org")
        assertThat(matchWithoutPort.groups["port"]).isNull()
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun javaDurationConversionsRoundTripSecondsNanosAndNegativeValues() {
        val javaDuration = JavaDuration.ofDays(1)
            .plusHours(2)
            .plusMinutes(3)
            .plusSeconds(4)
            .plusNanos(987_654_321)
        val kotlinDuration = javaDuration.toKotlinDuration()

        assertThat(kotlinDuration.toJavaDuration()).isEqualTo(javaDuration)
        assertThat(kotlinDuration.inWholeSeconds).isEqualTo(javaDuration.seconds)
        assertThat(kotlinDuration.inWholeMilliseconds).isEqualTo(javaDuration.toMillis())

        val negativeDuration = JavaDuration.ofSeconds(-3, 250_000_000)
            .toKotlinDuration()
            .toJavaDuration()
        assertThat(negativeDuration).isEqualTo(JavaDuration.ofMillis(-2_750))
    }

    @Test
    fun jdk8MapHelpersUseDefaultsAndConditionalRemoval() {
        val immutableScores: Map<String, Int> = linkedMapOf("tests" to 4, "metadata" to 2)
        assertThat(immutableScores.getOrDefault("tests", -1)).isEqualTo(4)
        assertThat(immutableScores.getOrDefault("native-image", -1)).isEqualTo(-1)

        val mutableLabels = linkedMapOf("language" to "kotlin", "target" to "jvm")
        assertThat(mutableLabels.remove("language", "java")).isFalse()
        assertThat(mutableLabels).containsEntry("language", "kotlin")
        assertThat(mutableLabels.remove("language", "kotlin")).isTrue()
        assertThat(mutableLabels).containsExactlyEntriesOf(mapOf("target" to "jvm"))
    }

    @Test
    fun defaultRandomSupportsJdk8BoundedGeneration() {
        repeat(100) {
            val intValue = Random.Default.nextInt(10, 20)
            val longValue = Random.Default.nextLong(100L, 200L)
            val doubleValue = Random.Default.nextDouble(0.25, 0.75)

            assertThat(intValue).isBetween(10, 19)
            assertThat(longValue).isBetween(100L, 199L)
            assertThat(doubleValue).isGreaterThanOrEqualTo(0.25).isLessThan(0.75)
        }
    }
}
