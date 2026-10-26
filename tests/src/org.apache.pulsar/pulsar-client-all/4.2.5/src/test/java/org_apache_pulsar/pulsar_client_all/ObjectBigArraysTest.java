/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.it.unimi.dsi.fastutil.objects.ObjectBigArrays;
import org.junit.jupiter.api.Test;

public class ObjectBigArraysTest {
    @Test
    void allocatesTypedBigArraysFromClassAndPrototype() {
        String[][] fromClass = (String[][]) ObjectBigArrays.newBigArray(String[].class, 3);
        String[][] fromPrototype = ObjectBigArrays.newBigArray(new String[0][], 2);
        ObjectBigArrays.set(fromClass, 1, "pulsar");
        ObjectBigArrays.set(fromPrototype, 0, "bookkeeper");

        assertThat(fromClass).isInstanceOf(String[][].class);
        assertThat(ObjectBigArrays.get(fromClass, 1)).isEqualTo("pulsar");
        assertThat(ObjectBigArrays.get(fromPrototype, 0)).isEqualTo("bookkeeper");
    }
}
