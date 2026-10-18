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
    void createsGrowsAndRemovesTypedArrays() {
        final String[] grown = ArrayUtils.add(new String[] {"alpha"}, "beta");
        final String[] createdFromNull = ArrayUtils.add((String[]) null, "first");
        final String[] inserted = ArrayUtils.add(grown, 1, "between");
        final String[] indexedFromNull = ArrayUtils.add((String[]) null, 0, "indexed");

        assertThat(grown).containsExactly("alpha", "beta");
        assertThat(createdFromNull).containsExactly("first");
        assertThat(inserted).containsExactly("alpha", "between", "beta");
        assertThat(indexedFromNull).containsExactly("indexed");
        assertThat(ArrayUtils.newInstance(String.class, 2)).hasSize(2);
        assertThat(ArrayUtils.nullToEmpty(null, String[].class)).isEmpty();
        assertThat(ArrayUtils.remove(inserted, 1)).containsExactly("alpha", "beta");
        assertThat(ArrayUtils.removeAll(inserted, 0, 2)).containsExactly("between");
        assertThat(ArrayUtils.removeAllOccurrences(inserted, "alpha")).containsExactly("between", "beta");
    }
}
