/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_github_resilience4j.resilience4j_core;

import io.github.resilience4j.core.ClassUtils;
import io.github.resilience4j.core.IntervalBiFunction;
import io.github.resilience4j.core.functions.Either;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassUtilsTest {

    @Test
    void instantiatesIntervalBiFunctionUsingPublicNoArgConstructor() {
        IntervalBiFunction<String> intervalBiFunction = ClassUtils.instantiateIntervalBiFunctionClass(
            LinearIntervalBiFunction.class);

        Long interval = intervalBiFunction.apply(4, Either.right("result"));

        assertThat(interval).isEqualTo(40L);
    }

    @Test
    void instantiatesPredicateUsingPublicNoArgConstructor() {
        Predicate<String> predicate = ClassUtils.instantiatePredicateClass(StartsWithResilience.class);

        assertThat(predicate).accepts("resilience4j-core");
        assertThat(predicate).rejects("core");
    }

    @Test
    void instantiatesBiConsumerUsingPublicNoArgConstructor() {
        BiConsumer<Integer, List<String>> biConsumer = ClassUtils.instantiateBiConsumer(
            IndexedCollector.class);
        List<String> values = new ArrayList<>();

        biConsumer.accept(2, values);

        assertThat(values).containsExactly("attempt-2");
    }

    @Test
    void instantiatesFunctionUsingPublicNoArgConstructor() {
        Function<String, Integer> function = ClassUtils.instantiateFunction(StringLength.class);

        assertThat(function.apply("resilience4j")).isEqualTo(12);
    }

    @Test
    void instantiatesClassUsingDefaultConstructor() {
        DefaultConstructedComponent component = ClassUtils.instantiateClassDefConstructor(
            DefaultConstructedComponent.class);

        assertThat(component.name()).isEqualTo("default-component");
    }

    public static final class LinearIntervalBiFunction implements IntervalBiFunction<String> {

        public LinearIntervalBiFunction() {
        }

        @Override
        public Long apply(Integer attempt, Either<Throwable, String> either) {
            assertThat(either.get()).isEqualTo("result");
            return attempt * 10L;
        }
    }

    public static final class StartsWithResilience implements Predicate<String> {

        public StartsWithResilience() {
        }

        @Override
        public boolean test(String value) {
            return value.startsWith("resilience");
        }
    }

    public static final class IndexedCollector implements BiConsumer<Integer, List<String>> {

        public IndexedCollector() {
        }

        @Override
        public void accept(Integer attempt, List<String> values) {
            values.add("attempt-" + attempt);
        }
    }

    public static final class StringLength implements Function<String, Integer> {

        public StringLength() {
        }

        @Override
        public Integer apply(String value) {
            return value.length();
        }
    }

    public static final class DefaultConstructedComponent {

        public DefaultConstructedComponent() {
        }

        public DefaultConstructedComponent(String ignored) {
        }

        String name() {
            return "default-component";
        }
    }
}
