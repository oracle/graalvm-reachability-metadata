/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_junit_jupiter.junit_jupiter_params;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;

public class EmptyArgumentsProviderTest {

    @ParameterizedTest
    @EmptySource
    void providesEmptyArray(String[] values) {
        assertThat(values).isEmpty();
    }

    @ParameterizedTest
    @EmptySource
    void providesEmptyConcreteCollection(ArrayList<String> values) {
        assertThat(values).isEmpty();
    }
}
