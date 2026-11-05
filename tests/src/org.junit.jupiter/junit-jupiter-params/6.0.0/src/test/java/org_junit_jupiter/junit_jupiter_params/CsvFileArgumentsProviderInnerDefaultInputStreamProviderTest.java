/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_junit_jupiter.junit_jupiter_params;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

public class CsvFileArgumentsProviderInnerDefaultInputStreamProviderTest {

    @ParameterizedTest
    @CsvFileSource(resources = "/org_junit_jupiter/junit_jupiter_params/words.csv", numLinesToSkip = 1)
    void providesParsedAndConvertedArgumentsFromClasspathResource(String word, int expectedLength) {
        assertThat(word).isIn("cedar", "maple, oak", "").hasSize(expectedLength);
    }
}
