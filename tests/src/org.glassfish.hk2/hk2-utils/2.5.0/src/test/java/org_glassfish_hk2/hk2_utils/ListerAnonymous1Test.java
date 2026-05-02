/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_hk2.hk2_utils;

import org.jvnet.tiger_types.Lister;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ListerAnonymous1Test {
    @Test
    public void convertsAccumulatedItemsToTypedArray() {
        final Lister<?> lister = Lister.create(String[].class, String[].class);
        lister.add("alpha");
        lister.add("beta");

        final Object collection = lister.toCollection();

        assertThat(collection).isInstanceOf(String[].class);
        assertThat((String[]) collection).containsExactly("alpha", "beta");
    }
}
