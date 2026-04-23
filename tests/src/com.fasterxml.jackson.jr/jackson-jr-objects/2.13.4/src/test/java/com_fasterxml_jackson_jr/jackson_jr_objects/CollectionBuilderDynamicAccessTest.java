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
    void readsTypedArraysWithEmptySingletonAndMultiValueInputs() throws Exception {
        assertThat(JSON.std.arrayOfFrom(String.class, "[]")).isEmpty();
        assertThat(JSON.std.arrayOfFrom(String.class, "[\"solo\"]")).containsExactly("solo");
        assertThat(JSON.std.arrayOfFrom(String.class, "[\"left\",\"right\"]"))
                .containsExactly("left", "right");
    }
}
