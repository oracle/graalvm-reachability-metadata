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
    void copiesSubListElementsIntoRequestedArrayType() {
        ObjectImmutableList<CharSequence> values =
                ObjectImmutableList.of("ledger", "pulsar", "bookkeeper", "broker");
        ObjectList<CharSequence> middle = values.subList(1, 3);

        CharSequence[] copied = middle.toArray(new CharSequence[0]);

        assertThat(copied).isInstanceOf(CharSequence[].class).containsExactly("pulsar", "bookkeeper");
    }
}
