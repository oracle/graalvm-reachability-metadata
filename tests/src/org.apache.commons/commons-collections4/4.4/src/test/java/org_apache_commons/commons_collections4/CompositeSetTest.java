/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_collections4;

import org.apache.commons.collections4.set.CompositeSet;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class CompositeSetTest {

    @Test
    void createsTypedArrayWhenProvidedArrayIsTooSmall() {
        Set<String> first = new LinkedHashSet<>(List.of("alpha", "beta"));
        Set<String> second = new LinkedHashSet<>(List.of("gamma"));
        CompositeSet<String> compositeSet = new CompositeSet<>(first, second);

        String[] target = new String[1];
        String[] values = compositeSet.toArray(target);

        assertThat(values)
                .isNotSameAs(target)
                .containsExactly("alpha", "beta", "gamma");
    }
}
