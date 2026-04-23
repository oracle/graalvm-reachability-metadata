/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_jr.jackson_jr_objects;

import com.fasterxml.jackson.jr.ob.api.CollectionBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CollectionBuilderDynamicAccessTest {
    @Test
    void createsEmptyTypedArrays() {
        CollectionBuilder builder = CollectionBuilder.defaultImpl();

        String[] values = builder.emptyArray(String.class);

        assertThat(values).isEmpty();
        assertThat(values.getClass().getComponentType()).isSameAs(String.class);
    }

    @Test
    void createsSingletonTypedArrays() {
        CollectionBuilder builder = CollectionBuilder.defaultImpl();

        String[] values = builder.singletonArray(String.class, "solo");

        assertThat(values).containsExactly("solo");
        assertThat(values.getClass().getComponentType()).isSameAs(String.class);
    }

    @Test
    void createsMultiValueTypedArrays() {
        CollectionBuilder builder = CollectionBuilder.defaultImpl();

        String[] values = builder.start()
                .add("left")
                .add("right")
                .buildArray(String.class);

        assertThat(values).containsExactly("left", "right");
        assertThat(values.getClass().getComponentType()).isSameAs(String.class);
    }
}
