/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_wiremock.wiremock;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.common.ArrayFunctions;
import org.junit.jupiter.api.Test;

public class ArrayFunctionsTest {
    @Test
    void prependCreatesTypedArrayWithPrependedElement() {
        String[] original = {"second", "third"};

        String[] result = ArrayFunctions.prepend("first", original);

        assertThat(result).containsExactly("first", "second", "third");
        assertThat(result).isInstanceOf(String[].class);
        assertThat(original).containsExactly("second", "third");
    }
}
