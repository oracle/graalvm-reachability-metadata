/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.org.apache.commons.lang3.ArrayUtils;
import org.junit.jupiter.api.Test;

public class ArrayUtilsTest {
    @Test
    void createsGrowsAndFiltersTypedArrays() {
        assertThat(ArrayUtils.add(new String[] {"alpha", "gamma"}, 1, "beta"))
                .containsExactly("alpha", "beta", "gamma");
        assertThat(ArrayUtils.add((String[]) null, "first")).containsExactly("first");
        assertThat(ArrayUtils.newInstance(String.class, 2)).hasSize(2);
        assertThat(ArrayUtils.nullToEmpty(null, String[].class)).isEmpty();
        assertThat(ArrayUtils.remove(new String[] {"alpha", "beta", "gamma"}, 1))
                .containsExactly("alpha", "gamma");
        assertThat(ArrayUtils.removeAll(new String[] {"zero", "one", "two", "three"}, 1, 3))
                .containsExactly("zero", "two");
        assertThat(ArrayUtils.removeElements(new String[] {"a", "b", "a", "c"}, "a", "c"))
                .containsExactly("b", "a");
    }
}
