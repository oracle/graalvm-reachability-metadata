/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_junit_jupiter.junit_jupiter_params;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.jupiter.params.provider.ValueSource;

class JunitJupiterParamsTest {

    @ParameterizedTest
    @ValueSource(strings = {"alpha", "beta"})
    void providesArgumentsFromValueSource(String value) {
        assertThat(value).isNotBlank();
    }

    @ParameterizedTest
    @ArgumentsSource(WordArgumentsProvider.class)
    void providesArgumentsFromCustomSource(String value, int length) {
        assertThat(value).hasSize(length);
    }

    public static class WordArgumentsProvider implements ArgumentsProvider {

        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
            return Stream.of(
                    Arguments.of("jupiter", 7),
                    Arguments.of("params", 6));
        }
    }
}
