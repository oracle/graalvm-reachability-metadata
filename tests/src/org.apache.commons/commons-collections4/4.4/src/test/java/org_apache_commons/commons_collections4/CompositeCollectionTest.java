/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_collections4;

import org.apache.commons.collections4.collection.CompositeCollection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class CompositeCollectionTest {

    @Test
    void createsTypedArrayWhenProvidedArrayIsTooSmall() {
        CompositeCollection<String> compositeCollection = new CompositeCollection<>(
                List.of("alpha", "beta"),
                List.of("gamma")
        );

        String[] target = new String[1];
        String[] values = compositeCollection.toArray(target);

        assertThat(values)
                .isNotSameAs(target)
                .containsExactly("alpha", "beta", "gamma");
    }
}
