/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_jcr_commons;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.jackrabbit.util.WeakIdentityCollection;
import org.junit.jupiter.api.Test;

public class WeakIdentityCollectionTest {
    @Test
    public void toArrayCreatesTypedArrayWhenProvidedArrayIsTooSmall() {
        WeakIdentityCollection collection = new WeakIdentityCollection(1);
        String first = "first";
        String second = "second";

        collection.add(first);
        collection.add(second);

        Object[] values = collection.toArray(new String[0]);

        assertThat(values).isInstanceOf(String[].class);
        assertThat(values).containsExactly(first, second);
    }
}
