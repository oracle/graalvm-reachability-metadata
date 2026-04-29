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
    void createsEmptyTypedArraysDirectly() throws Exception {
        CollectionBuilder builder = CollectionBuilder.defaultImpl();
        Class<ArrayElement> elementType = runtimeArrayElementType();

        Object[] values = builder.emptyArray(elementType);

        assertThat(values).isEmpty();
        assertThat(values.getClass().getComponentType()).isSameAs(elementType);
    }

    @Test
    void createsSingletonTypedArraysDirectly() throws Exception {
        CollectionBuilder builder = CollectionBuilder.defaultImpl();
        Class<ArrayElement> elementType = runtimeArrayElementType();

        Object[] values = builder.singletonArray(elementType, new ArrayElement("solo"));

        assertThat(values).singleElement().isInstanceOf(ArrayElement.class);
        assertThat(((ArrayElement) values[0]).name).isEqualTo("solo");
        assertThat(values.getClass().getComponentType()).isSameAs(elementType);
    }

    @Test
    void createsMultiValueTypedArraysDirectly() throws Exception {
        CollectionBuilder builder = CollectionBuilder.defaultImpl();
        Class<ArrayElement> elementType = runtimeArrayElementType();

        Object[] values = builder.start()
                .add(new ArrayElement("left"))
                .add(new ArrayElement("right"))
                .buildArray(elementType);

        assertThat(values).hasSize(2);
        assertThat(((ArrayElement) values[0]).name).isEqualTo("left");
        assertThat(((ArrayElement) values[1]).name).isEqualTo("right");
        assertThat(values.getClass().getComponentType()).isSameAs(elementType);
    }

    @Test
    void readsEmptyTypedArraysThroughJsonApi() throws Exception {
        Class<String> elementType = runtimeStringType();

        Object[] values = JSON.std.arrayOfFrom(elementType, "[]");

        assertThat(values).isEmpty();
        assertThat(values).isInstanceOf(String[].class);
        assertThat(values.getClass().getComponentType()).isSameAs(elementType);
    }

    @Test
    void readsSingletonTypedArraysThroughJsonApi() throws Exception {
        Class<String> elementType = runtimeStringType();

        Object[] values = JSON.std.arrayOfFrom(elementType, "[\"solo\"]");

        assertThat(values).containsExactly("solo");
        assertThat(values).isInstanceOf(String[].class);
        assertThat(values.getClass().getComponentType()).isSameAs(elementType);
    }

    @Test
    void readsMultiValueTypedArraysThroughJsonApi() throws Exception {
        Class<String> elementType = runtimeStringType();

        Object[] values = JSON.std.arrayOfFrom(elementType, "[\"left\",\"right\"]");

        assertThat(values).containsExactly("left", "right");
        assertThat(values).isInstanceOf(String[].class);
        assertThat(values.getClass().getComponentType()).isSameAs(elementType);
    }

    @SuppressWarnings("unchecked")
    private static Class<ArrayElement> runtimeArrayElementType() throws Exception {
        return (Class<ArrayElement>) JSON.std.beanFrom(Class.class, '"' + ArrayElement.class.getName() + '"');
    }

    @SuppressWarnings("unchecked")
    private static Class<String> runtimeStringType() throws Exception {
        return (Class<String>) JSON.std.beanFrom(Class.class, '"' + String.class.getName() + '"');
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
