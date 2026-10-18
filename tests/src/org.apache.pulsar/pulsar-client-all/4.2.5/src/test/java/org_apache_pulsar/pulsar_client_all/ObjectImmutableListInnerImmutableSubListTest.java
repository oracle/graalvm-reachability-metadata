/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import org.apache.pulsar.shade.it.unimi.dsi.fastutil.objects.ObjectList;
import org.junit.jupiter.api.Test;

public class ObjectImmutableListInnerImmutableSubListTest {

    @Test
    void copiesAnImmutableSublistIntoATypedArray() {
        final ObjectList<String> values = ObjectImmutableList.of("zero", "one", "two").subList(1, 3);

        assertThat(values.toArray(new String[0])).containsExactly("one", "two");
    }
}
