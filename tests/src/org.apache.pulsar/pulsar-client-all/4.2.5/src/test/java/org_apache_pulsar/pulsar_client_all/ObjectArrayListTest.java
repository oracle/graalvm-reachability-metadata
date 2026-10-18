/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.junit.jupiter.api.Test;

public class ObjectArrayListTest {

    @Test
    void copiesElementsIntoATypedArray() {
        final ObjectArrayList<String> values = new ObjectArrayList<>(new String[] {"one", "two"});

        final String[] copied = values.toArray(new String[0]);

        assertThat(copied).containsExactly("one", "two");
        assertThat(copied.getClass()).isEqualTo(String[].class);
    }
}
