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
        Class<String> elementType = runtimeStringType();

        Object[] values = JSON.std.arrayOfFrom(elementType, "[]");

        assertThat(values).isEmpty();
        assertThat(values).isInstanceOf(String[].class);
        assertThat(values.getClass().getComponentType()).isSameAs(elementType);
    }

    @Test
    void readsSingletonTypedArrays() throws Exception {
        Class<String> elementType = runtimeStringType();

        Object[] values = JSON.std.arrayOfFrom(elementType, "[\"solo\"]");

        assertThat(values).containsExactly("solo");
        assertThat(values).isInstanceOf(String[].class);
        assertThat(values.getClass().getComponentType()).isSameAs(elementType);
    }

    @Test
    void readsMultiValueTypedArrays() throws Exception {
        Class<String> elementType = runtimeStringType();

        Object[] values = JSON.std.arrayOfFrom(elementType, "[\"left\",\"right\"]");

        assertThat(values).containsExactly("left", "right");
        assertThat(values).isInstanceOf(String[].class);
        assertThat(values.getClass().getComponentType()).isSameAs(elementType);
    }

    @SuppressWarnings("unchecked")
    private static Class<String> runtimeStringType() throws Exception {
        return (Class<String>) JSON.std.beanFrom(Class.class, '"' + String.class.getName() + '"');
    }

    public static final class ArrayElement {
        private ArrayElement() {
        }
    }
}
