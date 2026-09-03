/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_collections.eclipse_collections;

import org.eclipse.collections.impl.list.fixed.AbstractArrayAdapter;
import org.eclipse.collections.impl.list.fixed.ArrayAdapter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractArrayAdapterTest {

    @Test
    void createsTypedArrayWhenTargetArrayIsTooSmall() {
        AbstractArrayAdapter<String> adapter = ArrayAdapter.newArrayWith("alpha", "beta", "gamma");
        String[] target = new String[1];

        String[] result = adapter.toArray(target);

        assertThat(result)
                .isNotSameAs(target)
                .isInstanceOf(String[].class)
                .containsExactly("alpha", "beta", "gamma");
    }
}
