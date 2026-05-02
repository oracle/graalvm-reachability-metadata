/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_hk2.hk2_utils;

import java.util.ArrayList;
import java.util.List;

import org.jvnet.tiger_types.Lister;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ListerTest {
    @Test
    public void createsRequestedConcreteCollectionType() {
        final Lister<?> lister = Lister.create(ArrayList.class, ArrayList.class);
        lister.add("alpha");
        lister.add("beta");

        final Object collection = lister.toCollection();

        assertThat(collection).isInstanceOf(ArrayList.class);
        assertThat(collection).isEqualTo(List.of("alpha", "beta"));
    }

    @Test
    public void fallsBackToConcreteCollectionForCollectionInterface() {
        final Lister<?> lister = Lister.create(List.class, List.class);
        lister.add("alpha");
        lister.add("beta");

        final Object collection = lister.toCollection();

        assertThat(collection).isInstanceOf(ArrayList.class);
        assertThat(collection).isEqualTo(List.of("alpha", "beta"));
    }
}
