/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_janino.janino;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.codehaus.janino.util.iterator.Iterables;
import org.junit.jupiter.api.Test;

public class IterablesTest {
    @Test
    void toArrayCreatesArrayWithRequestedComponentType() {
        final List<String> values = Arrays.asList("left", "right");

        final String[] result = Iterables.toArray(values, String.class);

        assertThat(result).containsExactly("left", "right");
        assertThat(result.getClass().getComponentType()).isEqualTo(String.class);
    }
}
