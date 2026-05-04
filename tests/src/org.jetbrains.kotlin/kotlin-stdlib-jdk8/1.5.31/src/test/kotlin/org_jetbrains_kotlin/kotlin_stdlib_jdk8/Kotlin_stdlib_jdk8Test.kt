/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_stdlib_jdk8

import java.time.Duration as JavaDuration
import java.util.concurrent.atomic.AtomicBoolean
import java.util.stream.Collectors
import java.util.stream.DoubleStream
import java.util.stream.IntStream
import java.util.stream.LongStream
import java.util.stream.Stream
import kotlin.random.Random
import kotlin.time.ExperimentalTime
import kotlin.time.toJavaDuration
import kotlin.time.toKotlinDuration
import kotlin.streams.asSequence
import kotlin.streams.asStream
import kotlin.streams.toList as streamToList
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

public class Kotlin_stdlib_jdk8Test {
    @Test
    public fun objectStreamAsSequenceKeepsStreamOrderingAndLazyOperations() {
        val closeCalled: AtomicBoolean = AtomicBoolean(false)
        val stream: Stream<String> = Stream.of("alpha", "beta", "gamma", "delta")
            .onClose { closeCalled.set(true) }

        val result: List<String> = try {
            stream.asSequence()
                .filter { value: String -> value.contains('a') }
                .map { value: String -> value.uppercase() }
                .take(3)
                .toList()
        } finally {
            stream.close()
        }

        assertThat(result).containsExactly("ALPHA", "BETA", "GAMMA")
        assertThat(closeCalled.get()).isTrue()
    }

    @Test
    public fun primitiveStreamsConvertToKotlinSequencesAndLists() {
        val squares: List<Int> = IntStream.rangeClosed(1, 5)
            .asSequence()
            .map { value: Int -> value * value }
            .toList()
        val descendingLongs: List<Long> = LongStream.of(9L, 7L, 5L, 3L).streamToList()
        val doubleTotal: Double = DoubleStream.of(1.25, 2.5, 3.75)
            .asSequence()
            .sum()

        assertThat(squares).containsExactly(1, 4, 9, 16, 25)
        assertThat(descendingLongs).containsExactly(9L, 7L, 5L, 3L)
        assertThat(doubleTotal).isEqualTo(7.5)
    }

    @Test
    public fun sequenceAsStreamSupportsJavaStreamPipelines() {
        val values: Sequence<Int> = generateSequence(1) { previous: Int ->
            if (previous < 6) previous + 1 else null
        }
        val stream: Stream<Int> = values.asStream()

        val transformedValues: List<String> = try {
            stream
                .filter { value: Int -> value % 2 == 0 }
                .map { value: Int -> "value-$value" }
                .collect(Collectors.toList())
        } finally {
            stream.close()
        }

        assertThat(transformedValues).containsExactly("value-2", "value-4", "value-6")
    }

    @Test
    public fun sequenceAsStreamCanRunParallelPrimitiveTerminalOperations() {
        val stream: Stream<Int> = sequenceOf(2, 4, 6, 8).asStream()

        val sum: Int = try {
            stream.parallel()
                .mapToInt { value: Int -> value }
                .sum()
        } finally {
            stream.close()
        }

        assertThat(sum).isEqualTo(20)
    }

    @Test
    public fun streamToListCollectsObjectAndPrimitiveStreamElements() {
        val words: List<String> = Stream.of("native", "image", "metadata").streamToList()
        val indexes: List<Int> = IntStream.range(0, 4).streamToList()
        val measurements: List<Double> = DoubleStream.of(0.5, 1.5, 2.5).streamToList()

        assertThat(words).containsExactly("native", "image", "metadata")
        assertThat(indexes).containsExactly(0, 1, 2, 3)
        assertThat(measurements).containsExactly(0.5, 1.5, 2.5)
    }

    @Test
    public fun jdk8MapExtensionsReturnDefaultsAndRemoveOnlyMatchingEntries() {
        val countsByName: Map<String, Int> = mapOf("alpha" to 3, "beta" to 0)
        val statesByTask: MutableMap<String, String> = linkedMapOf(
            "compile" to "queued",
            "test" to "running",
        )

        val existingZeroCount: Int = countsByName.getOrDefault("beta", -1)
        val missingCount: Int = countsByName.getOrDefault("gamma", -1)
        val mismatchedRemoval: Boolean = statesByTask.remove("compile", "running")
        val matchedRemoval: Boolean = statesByTask.remove("compile", "queued")
        val missingRemoval: Boolean = statesByTask.remove("package", "queued")

        assertThat(existingZeroCount).isEqualTo(0)
        assertThat(missingCount).isEqualTo(-1)
        assertThat(mismatchedRemoval).isFalse()
        assertThat(matchedRemoval).isTrue()
        assertThat(missingRemoval).isFalse()
        assertThat(statesByTask).containsOnlyKeys("test")
    }

    @Test
    public fun namedRegexGroupsAreAvailableThroughMatchGroupCollection() {
        val uriPattern: Regex = Regex("""(?<scheme>[a-z]+)://(?<host>[\w.-]+):(?<port>\d+)(?<query>\?.*)?""")
        val match: MatchResult = requireNotNull(uriPattern.matchEntire("https://graal.example:443"))

        val scheme: MatchGroup? = match.groups["scheme"]
        val host: MatchGroup? = match.groups["host"]
        val port: MatchGroup? = match.groups["port"]

        assertThat(scheme?.value).isEqualTo("https")
        assertThat(scheme?.range).isEqualTo(0..4)
        assertThat(host?.value).isEqualTo("graal.example")
        assertThat(host?.range).isEqualTo(8..20)
        assertThat(port?.value).isEqualTo("443")
        assertThat(port?.range).isEqualTo(22..24)
        assertThat(match.groups["query"]).isNull()
    }

    @OptIn(ExperimentalTime::class)
    @Test
    public fun durationsRoundTripThroughJavaTimeDurationConversions() {
        val positiveDuration: kotlin.time.Duration = JavaDuration.ofSeconds(2, 345_678_901).toKotlinDuration()
        val negativeDuration: kotlin.time.Duration = JavaDuration.ofSeconds(-2, 250_000_000).toKotlinDuration()

        assertThat(positiveDuration.inWholeSeconds).isEqualTo(2L)
        assertThat(positiveDuration.inWholeMilliseconds).isEqualTo(2_345L)
        assertThat(positiveDuration.toJavaDuration()).isEqualTo(JavaDuration.ofSeconds(2, 345_678_901))
        assertThat(negativeDuration.inWholeMilliseconds).isEqualTo(-1_750L)
        assertThat(negativeDuration.toJavaDuration()).isEqualTo(JavaDuration.ofSeconds(-2, 250_000_000))
    }

    @Test
    public fun defaultRandomProducesValuesInsideRequestedJdk8Bounds() {
        val ints: List<Int> = List(64) { Random.nextInt(from = 10, until = 20) }
        val longs: List<Long> = List(64) { Random.nextLong(until = 100L) }
        val doubles: List<Double> = List(64) { Random.nextDouble(from = 2.0, until = 3.0) }

        assertThat(ints).allSatisfy { value: Int ->
            assertThat(value).isGreaterThanOrEqualTo(10).isLessThan(20)
        }
        assertThat(longs).allSatisfy { value: Long ->
            assertThat(value).isGreaterThanOrEqualTo(0L).isLessThan(100L)
        }
        assertThat(doubles).allSatisfy { value: Double ->
            assertThat(value).isGreaterThanOrEqualTo(2.0).isLessThan(3.0)
        }
    }
}
