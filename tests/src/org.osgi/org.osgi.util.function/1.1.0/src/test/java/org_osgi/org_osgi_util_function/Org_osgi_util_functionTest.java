/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_osgi.org_osgi_util_function;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.osgi.util.function.Consumer;
import org.osgi.util.function.Function;
import org.osgi.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Org_osgi_util_functionTest {
    @Test
    void functionAppliesTransformationsAndCanReturnNull() throws Exception {
        Function<String, Integer> parseInteger = Integer::parseInt;
        Function<String, String> trimToUpperCaseOrNull = value -> {
            String trimmed = value.trim();
            return trimmed.isEmpty() ? null : trimmed.toUpperCase(Locale.ROOT);
        };

        assertThat(parseInteger.apply("41")).isEqualTo(41);
        assertThat(trimToUpperCaseOrNull.apply("  osgi  ")).isEqualTo("OSGI");
        assertThat(trimToUpperCaseOrNull.apply("   ")).isNull();
    }

    @Test
    void predicateControlsFilteringLogic() throws Exception {
        Predicate<String> hasOnlyLetters = Org_osgi_util_functionTest::hasOnlyLetters;
        Predicate<String> hasAtLeastFourCharacters = value -> value.length() >= 4;

        List<String> alphaNumericValues = List.of("osgi", "r7", "test", "api", "function");
        List<String> alphabeticValues = filter(alphaNumericValues, hasOnlyLetters);
        List<String> longAlphabeticValues = filter(alphabeticValues, hasAtLeastFourCharacters);

        assertThat(alphabeticValues).containsExactly("osgi", "test", "api", "function");
        assertThat(longAlphabeticValues).containsExactly("osgi", "test", "function");
    }

    @Test
    void consumerReceivesValuesInEncounterOrder() throws Exception {
        List<String> acceptedValues = new ArrayList<>();
        Consumer<String> rememberValue = acceptedValues::add;

        forEach(List.of("first", "second", "third"), rememberValue);

        assertThat(acceptedValues).containsExactly("first", "second", "third");
    }

    @Test
    void interfacesCanBeCombinedInCheckedPipeline() throws Exception {
        Function<String, Integer> parseInteger = value -> {
            if (value.isBlank()) {
                throw new InvalidInputException("Blank values cannot be parsed");
            }
            return Integer.parseInt(value.trim());
        };
        Predicate<Integer> isEven = value -> value % 2 == 0;
        List<String> sink = new ArrayList<>();
        Consumer<Integer> formatResult = value -> sink.add("number=" + value);

        List<Integer> numbers = map(List.of(" 1 ", "2", " 4", "7"), parseInteger);
        List<Integer> evenNumbers = filter(numbers, isEven);
        forEach(evenNumbers, formatResult);

        assertThat(numbers).containsExactly(1, 2, 4, 7);
        assertThat(evenNumbers).containsExactly(2, 4);
        assertThat(sink).containsExactly("number=2", "number=4");
    }

    @Test
    void functionCanUseConstructorReferencesAsFactories() throws Exception {
        Function<Integer, List<String>> newListWithCapacity = ArrayList::new;

        List<String> values = newListWithCapacity.apply(2);
        values.add("osgi");
        values.add("function");

        assertThat(values).containsExactly("osgi", "function");
    }

    @Test
    void checkedExceptionsPropagateFromImplementations() {
        InvalidInputException consumerFailure = new InvalidInputException("consumer failure");
        InvalidInputException functionFailure = new InvalidInputException("function failure");
        InvalidInputException predicateFailure = new InvalidInputException("predicate failure");
        Consumer<String> failingConsumer = value -> {
            throw consumerFailure;
        };
        Function<String, Integer> failingFunction = value -> {
            throw functionFailure;
        };
        Predicate<String> failingPredicate = value -> {
            throw predicateFailure;
        };

        assertThatThrownBy(() -> failingConsumer.accept("value")).isSameAs(consumerFailure);
        assertThatThrownBy(() -> failingFunction.apply("value")).isSameAs(functionFailure);
        assertThatThrownBy(() -> failingPredicate.test("value")).isSameAs(predicateFailure);
    }

    @Test
    void interfacesCanBeImplementedByReusableStatefulClasses() throws Exception {
        PrefixFunction prefixFunction = new PrefixFunction("osgi-");
        ContainsTextPredicate containsApi = new ContainsTextPredicate("api");
        RecordingConsumer recordingConsumer = new RecordingConsumer();

        String functionValue = prefixFunction.apply("function");
        String apiValue = prefixFunction.apply("api");

        if (containsApi.test(functionValue)) {
            recordingConsumer.accept(functionValue);
        }
        if (containsApi.test(apiValue)) {
            recordingConsumer.accept(apiValue);
        }

        assertThat(functionValue).isEqualTo("osgi-function");
        assertThat(recordingConsumer.acceptedValues()).containsExactly("osgi-api");
    }

    private static boolean hasOnlyLetters(String value) throws Exception {
        if (value.isEmpty()) {
            throw new InvalidInputException("Empty values are not supported");
        }
        return value.chars().allMatch(Character::isLetter);
    }

    private static <T, R> List<R> map(Iterable<T> values, Function<? super T, ? extends R> mapper) throws Exception {
        List<R> mappedValues = new ArrayList<>();
        for (T value : values) {
            mappedValues.add(mapper.apply(value));
        }
        return mappedValues;
    }

    private static <T> List<T> filter(Iterable<T> values, Predicate<? super T> predicate) throws Exception {
        List<T> acceptedValues = new ArrayList<>();
        for (T value : values) {
            if (predicate.test(value)) {
                acceptedValues.add(value);
            }
        }
        return acceptedValues;
    }

    private static <T> void forEach(Iterable<T> values, Consumer<? super T> consumer) throws Exception {
        for (T value : values) {
            consumer.accept(value);
        }
    }

    private static final class PrefixFunction implements Function<String, String> {
        private final String prefix;

        private PrefixFunction(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public String apply(String value) {
            return prefix + value;
        }
    }

    private static final class ContainsTextPredicate implements Predicate<String> {
        private final String text;

        private ContainsTextPredicate(String text) {
            this.text = text;
        }

        @Override
        public boolean test(String value) {
            return value.contains(text);
        }
    }

    private static final class RecordingConsumer implements Consumer<String> {
        private final List<String> acceptedValues = new ArrayList<>();

        @Override
        public void accept(String value) {
            acceptedValues.add(value);
        }

        private List<String> acceptedValues() {
            return acceptedValues;
        }
    }

    private static final class InvalidInputException extends Exception {
        private InvalidInputException(String message) {
            super(message);
        }
    }
}
