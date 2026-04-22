/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_immutables.value;

import org.immutables.value.Value;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValueTest {

    @BeforeEach
    void resetLazyCounter() {
        ValidatedRange.resetLazyCalls();
    }

    @Test
    void immutableBuilderSupportsDefaultsDerivedValuesCollectionsAndCopying() {
        List<String> seedTags = new ArrayList<>(List.of("core"));
        Map<String, Integer> seedScores = new LinkedHashMap<>();
        seedScores.put("math", 10);

        ImmutablePerson person = ImmutablePerson.builder()
                .name("Ada")
                .nickname("Ace")
                .addTags("founder")
                .addAllTags(seedTags)
                .putAllScores(seedScores)
                .putScores("logic", 9)
                .secret("hidden")
                .build();

        seedTags.add("mutated-after-build");
        seedScores.put("mutated-after-build", 0);

        assertThat(person.name()).isEqualTo("Ada");
        assertThat(person.age()).isEqualTo(18);
        assertThat(person.nickname()).contains("Ace");
        assertThat(person.tags()).containsExactly("founder", "core");
        assertThat(person.scores()).containsEntry("math", 10).containsEntry("logic", 9);
        assertThat(person.label()).isEqualTo("Ada:18");
        assertThat(person.toString())
                .contains("name=Ada")
                .contains("age=18")
                .contains("nickname=Ace")
                .contains("label=Ada:18")
                .doesNotContain("hidden")
                .doesNotContain("secret");

        assertThatThrownBy(() -> person.tags().add("extra"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> person.scores().put("extra", 1))
                .isInstanceOf(UnsupportedOperationException.class);

        ImmutablePerson updated = person.withAge(21)
                .withNickname(Optional.empty())
                .withTags(List.of("replaced"))
                .withScores(Map.of("science", 11));

        assertThat(updated).isNotSameAs(person);
        assertThat(updated.age()).isEqualTo(21);
        assertThat(updated.nickname()).isEmpty();
        assertThat(updated.tags()).containsExactly("replaced");
        assertThat(updated.scores()).containsExactly(Map.entry("science", 11));
        assertThat(updated.label()).isEqualTo("Ada:21");

        assertThat(ImmutablePerson.copyOf(person)).isSameAs(person);
        assertThat(ImmutablePerson.builder().from(person).age(22).build())
                .usingRecursiveComparison()
                .isEqualTo(person.withAge(22));
    }

    @Test
    void immutableBuilderReportsMissingRequiredAttributes() {
        assertThatThrownBy(() -> ImmutablePerson.builder().build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("name")
                .hasMessageContaining("secret");
    }

    @Test
    void parameterFactoriesAndSingletonInstancesRemainConvenientAndStable() {
        ImmutablePair pair = ImmutablePair.of(3, 4);

        assertThat(pair.left()).isEqualTo(3);
        assertThat(pair.right()).isEqualTo(4);
        assertThat(pair).isEqualTo(ImmutablePair.builder().left(3).right(4).build());
        assertThat(ImmutablePair.copyOf(pair)).isSameAs(pair);
        assertThat(ImmutablePair.builder().from(pair).right(7).build())
                .isEqualTo(ImmutablePair.of(3, 7));
        assertThat(pair.withLeft(8)).isEqualTo(ImmutablePair.of(8, 4));

        ImmutableMarker singleton = ImmutableMarker.of();
        assertThat(ImmutableMarker.builder().build()).isSameAs(singleton);
        assertThat(ImmutableMarker.copyOf(singleton)).isSameAs(singleton);
        assertThat(singleton.toString()).isEqualTo("Marker{}");
    }

    @Test
    void checkMethodsAndLazyAttributesValidateAndCacheComputedState() {
        ImmutableValidatedRange range = ImmutableValidatedRange.builder()
                .min(2)
                .max(5)
                .build();

        assertThat(range.span()).isEqualTo(3);
        assertThat(range.span()).isEqualTo(3);
        assertThat(ValidatedRange.lazyCalls()).isEqualTo(1);

        ImmutableValidatedRange expanded = ImmutableValidatedRange.builder()
                .from(range)
                .max(8)
                .build();

        assertThat(expanded.span()).isEqualTo(6);
        assertThat(ValidatedRange.lazyCalls()).isEqualTo(2);
        assertThat(expanded).isEqualTo(range.withMax(8));
        assertThat(ImmutableValidatedRange.copyOf(range)).isSameAs(range);

        assertThatThrownBy(() -> ImmutableValidatedRange.builder().min(9).max(4).build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("min must not exceed max");
    }
}

@Value.Immutable
interface Person {
    String name();

    @Value.Default
    default int age() {
        return 18;
    }

    Optional<String> nickname();

    List<String> tags();

    Map<String, Integer> scores();

    @Value.Redacted
    String secret();

    @Value.Derived
    default String label() {
        return name() + ":" + age();
    }
}

@Value.Immutable
interface Pair {
    @Value.Parameter
    int left();

    @Value.Parameter
    int right();
}

@Value.Immutable(singleton = true)
interface Marker {
}

@Value.Immutable
abstract class ValidatedRange {
    private static final AtomicInteger LAZY_CALLS = new AtomicInteger();

    abstract int min();

    abstract int max();

    @Value.Check
    protected void validate() {
        if (min() > max()) {
            throw new IllegalStateException("min must not exceed max");
        }
    }

    @Value.Lazy
    int span() {
        LAZY_CALLS.incrementAndGet();
        return max() - min();
    }

    static int lazyCalls() {
        return LAZY_CALLS.get();
    }

    static void resetLazyCalls() {
        LAZY_CALLS.set(0);
    }
}
