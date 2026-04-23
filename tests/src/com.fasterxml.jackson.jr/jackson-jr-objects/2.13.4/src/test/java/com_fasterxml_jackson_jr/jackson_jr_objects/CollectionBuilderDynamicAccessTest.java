/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_jr.jackson_jr_objects;

import com.fasterxml.jackson.jr.ob.JSON;
import com.fasterxml.jackson.jr.ob.api.CollectionBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CollectionBuilderDynamicAccessTest {
    @Test
    void createsEmptyTypedArrays() throws Exception {
        CollectionBuilder builder = CollectionBuilder.defaultImpl();
        Class<?> elementType = runtimeArrayElementType();

        Object[] values = builder.emptyArray((Class) elementType);

        assertThat(values).isEmpty();
        assertThat(values.getClass().getComponentType()).isSameAs(elementType);
    }

    @Test
    void createsSingletonTypedArrays() throws Exception {
        CollectionBuilder builder = CollectionBuilder.defaultImpl();
        Class<?> elementType = runtimeArrayElementType();

        Object[] values = builder.singletonArray(elementType, new ArrayElement("solo"));

        assertThat(values).singleElement().isInstanceOf(ArrayElement.class);
        assertThat(((ArrayElement) values[0]).name).isEqualTo("solo");
        assertThat(values.getClass().getComponentType()).isSameAs(elementType);
    }

    @Test
    void createsMultiValueTypedArrays() throws Exception {
        CollectionBuilder builder = CollectionBuilder.defaultImpl();
        Class<?> elementType = runtimeArrayElementType();

        Object[] values = builder.start()
                .add(new ArrayElement("left"))
                .add(new ArrayElement("right"))
                .buildArray((Class) elementType);

        assertThat(values).hasSize(2);
        assertThat(((ArrayElement) values[0]).name).isEqualTo("left");
        assertThat(((ArrayElement) values[1]).name).isEqualTo("right");
        assertThat(values.getClass().getComponentType()).isSameAs(elementType);
    }

    private static Class<?> runtimeArrayElementType() throws Exception {
        return JSON.std.beanFrom(Class.class, '"' + ArrayElement.class.getName() + '"');
    }

    public static final class ArrayElement {
        public String name;

        public ArrayElement() {
        }

        public ArrayElement(String name) {
            this.name = name;
        }
    }
}
