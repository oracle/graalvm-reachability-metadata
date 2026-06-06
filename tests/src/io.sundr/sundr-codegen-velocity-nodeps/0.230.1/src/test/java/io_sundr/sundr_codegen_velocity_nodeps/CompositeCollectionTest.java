/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;

import static org.assertj.core.api.Assertions.assertThat;

import io.sundr.deps.org.apache.commons.collections.collection.CompositeCollection;
import java.util.Arrays;
import java.util.Collection;
import org.junit.jupiter.api.Test;

public class CompositeCollectionTest {

    @Test
    public void copiesElementsIntoNewTypedArrayWhenSuppliedArrayIsTooSmall() {
        Collection first = Arrays.asList("alpha", "beta");
        Collection second = Arrays.asList("gamma");
        CompositeCollection composite = new CompositeCollection(new Collection[] {first, second});

        Object[] values = composite.toArray(new String[0]);

        assertThat(values).isInstanceOf(String[].class);
        assertThat(values).containsExactly("alpha", "beta", "gamma");
    }
}
