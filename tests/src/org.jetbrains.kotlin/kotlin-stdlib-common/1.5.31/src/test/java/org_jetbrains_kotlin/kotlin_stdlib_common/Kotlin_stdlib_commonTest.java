/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_stdlib_common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import kotlin.KotlinVersion;
import kotlin.Lazy;
import kotlin.LazyKt;
import kotlin.LazyThreadSafetyMode;
import kotlin.Pair;
import kotlin.Triple;
import kotlin.TuplesKt;
import kotlin.collections.CollectionsKt;
import kotlin.sequences.Sequence;
import kotlin.sequences.SequencesKt;
import kotlin.text.StringsKt;
import org.junit.jupiter.api.Test;

public class Kotlin_stdlib_commonTest {

    @Test
    void tuplesAndVersionsExposeStableCommonApiContracts() {
        Pair<String, String> module = TuplesKt.to("stdlib", "common");
        Triple<String, String, String> coordinate = new Triple<>("org.jetbrains.kotlin", "kotlin-stdlib", "common");
        KotlinVersion version = new KotlinVersion(1, 5, 31);

        assertThat(module.getFirst()).isEqualTo("stdlib");
        assertThat(module.getSecond()).isEqualTo("common");
        assertThat(module.copy("kotlin", "common")).isEqualTo(new Pair<>("kotlin", "common"));
        assertThat(TuplesKt.toList(module)).containsExactly("stdlib", "common");

        assertThat(coordinate.component1()).isEqualTo("org.jetbrains.kotlin");
        assertThat(coordinate.component2()).isEqualTo("kotlin-stdlib");
        assertThat(coordinate.component3()).isEqualTo("common");
        assertThat(TuplesKt.toList(coordinate)).containsExactly("org.jetbrains.kotlin", "kotlin-stdlib", "common");

        assertThat(version.isAtLeast(1, 5)).isTrue();
        assertThat(version.isAtLeast(1, 6)).isFalse();
        assertThat(version.compareTo(new KotlinVersion(1, 5, 30))).isPositive();
        assertThat(version).hasToString("1.5.31");
    }

    @Test
    void collectionOperatorsTransformAndIndexData() {
        List<String> modules = CollectionsKt.listOf("vm", "api", "java", "kotlin", "metadata");

        List<String> substantialModules = CollectionsKt.filter(modules, module -> module.length() >= 4);
        Map<Integer, List<String>> byLength = CollectionsKt.groupBy(modules, String::length);
        Map<Character, String> byInitial = CollectionsKt.associateBy(modules, module -> module.charAt(0));
        List<String> longestFirst = CollectionsKt.sortedByDescending(modules, String::length);

        assertThat(substantialModules).containsExactly("java", "kotlin", "metadata");
        assertThat(byLength.get(2)).containsExactly("vm");
        assertThat(byLength.get(3)).containsExactly("api");
        assertThat(byLength.get(4)).containsExactly("java");
        assertThat(byLength.get(6)).containsExactly("kotlin");
        assertThat(byLength.get(8)).containsExactly("metadata");
        assertThat(byInitial).containsEntry('k', "kotlin").containsEntry('m', "metadata");
        assertThat(longestFirst).containsExactly("metadata", "kotlin", "java", "api", "vm");
    }

    @Test
    void stringUtilitiesSplitAndNormalizeCoordinates() {
        String paddedCoordinate = "  org.jetbrains.kotlin:kotlin-stdlib-common:1.5.31  ";
        String coordinate = StringsKt.trim(paddedCoordinate).toString();

        assertThat(coordinate).isEqualTo("org.jetbrains.kotlin:kotlin-stdlib-common:1.5.31");
        assertThat(StringsKt.substringBefore(coordinate, ":", "")).isEqualTo("org.jetbrains.kotlin");
        assertThat(StringsKt.substringAfter(coordinate, ":", "")).isEqualTo("kotlin-stdlib-common:1.5.31");
        assertThat(StringsKt.substringAfterLast(coordinate, ":", "")).isEqualTo("1.5.31");
        assertThat(StringsKt.removeSuffix("kotlin-stdlib-common", "-common")).isEqualTo("kotlin-stdlib");
        assertThat(StringsKt.padStart("31", 4, '0')).isEqualTo("0031");
        assertThat(StringsKt.lines("alpha\nbeta\r\ngamma")).containsExactly("alpha", "beta", "gamma");
    }

    @Test
    void sequencesStayLazyUntilATerminalOperationConsumesThem() {
        AtomicInteger transformations = new AtomicInteger();
        Sequence<Integer> doubled = SequencesKt.map(
            SequencesKt.sequenceOf(1, 2, 3, 4),
            value -> {
                transformations.incrementAndGet();
                return value * 2;
            }
        );

        assertThat(transformations.get()).isZero();
        assertThat(SequencesKt.first(doubled, value -> value > 4)).isEqualTo(6);
        assertThat(transformations.get()).isEqualTo(3);

        Sequence<Integer> generated = SequencesKt.generateSequence(1, previous -> previous < 6 ? previous + 1 : null);
        List<Integer> evenSquares = SequencesKt.toList(
            SequencesKt.map(
                SequencesKt.filter(generated, value -> value % 2 == 0),
                value -> value * value
            )
        );

        assertThat(evenSquares).containsExactly(4, 16, 36);
    }

    @Test
    void lazyValuesInitializeExactlyOnce() {
        AtomicInteger invocations = new AtomicInteger();
        Lazy<String> lazyValue = LazyKt.lazy(
            LazyThreadSafetyMode.NONE,
            () -> "computed-" + invocations.incrementAndGet()
        );

        assertThat(lazyValue.isInitialized()).isFalse();
        assertThat(lazyValue.getValue()).isEqualTo("computed-1");
        assertThat(lazyValue.getValue()).isEqualTo("computed-1");
        assertThat(lazyValue.isInitialized()).isTrue();
        assertThat(invocations.get()).isEqualTo(1);
    }
}
