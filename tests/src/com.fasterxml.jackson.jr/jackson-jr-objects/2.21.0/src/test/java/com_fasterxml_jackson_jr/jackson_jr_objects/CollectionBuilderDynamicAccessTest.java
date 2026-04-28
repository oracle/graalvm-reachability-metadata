/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_jr.jackson_jr_objects;

import com.fasterxml.jackson.jr.ob.JSON;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CollectionBuilderDynamicAccessTest {
    @Test
    void readsEmptyTypedArrays() throws Exception {
        ArrayElement[] values = JSON.std.arrayOfFrom(ArrayElement.class, "[]");

        assertThat(values).isEmpty();
        assertThat(values.getClass().getComponentType()).isSameAs(ArrayElement.class);
    }

    @Test
    void readsSingletonTypedArrays() throws Exception {
        ArrayElement[] values = JSON.std.arrayOfFrom(ArrayElement.class, "[{\"name\":\"solo\"}]");

        assertThat(values).singleElement().extracting(element -> element.name).isEqualTo("solo");
        assertThat(values.getClass().getComponentType()).isSameAs(ArrayElement.class);
    }

    @Test
    void readsMultiValueTypedArrays() throws Exception {
        ArrayElement[] values = JSON.std.arrayOfFrom(ArrayElement.class,
                "[{\"name\":\"left\"},{\"name\":\"right\"}]");

        assertThat(values).extracting(element -> element.name).containsExactly("left", "right");
        assertThat(values.getClass().getComponentType()).isSameAs(ArrayElement.class);
    }

    public static final class ArrayElement {
        public String name;
    }
}
