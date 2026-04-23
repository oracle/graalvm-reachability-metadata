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
    void createsEmptyTypedArrays() throws Exception {
        String[] values = JSON.std.arrayOfFrom(String.class, "[]");

        assertThat(values).isEmpty();
        assertThat(values.getClass().getComponentType()).isSameAs(String.class);
    }

    @Test
    void createsSingletonTypedArrays() throws Exception {
        String[] values = JSON.std.arrayOfFrom(String.class, "[\"solo\"]");

        assertThat(values).containsExactly("solo");
    }

    @Test
    void createsMultiValueTypedArrays() throws Exception {
        String[] values = JSON.std.arrayOfFrom(String.class, "[\"left\",\"right\"]");

        assertThat(values).containsExactly("left", "right");
    }
}
