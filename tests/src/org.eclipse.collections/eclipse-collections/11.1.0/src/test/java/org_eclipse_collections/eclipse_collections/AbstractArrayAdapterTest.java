/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_collections.eclipse_collections;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.collections.impl.list.fixed.ArrayAdapter;
import org.junit.jupiter.api.Test;

public class AbstractArrayAdapterTest {
    @Test
    void toArrayCreatesTypedArrayWhenSuppliedArrayIsTooSmall() {
        final ArrayAdapter<String> adapter = ArrayAdapter.newArrayWith("alpha", "bravo", "charlie");

        final CharSequence[] result = adapter.toArray(new CharSequence[0]);

        assertThat(result)
                .isInstanceOf(CharSequence[].class)
                .containsExactly("alpha", "bravo", "charlie");
    }
}
