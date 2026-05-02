/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_jr.jackson_jr_objects;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.fasterxml.jackson.jr.ob.JSON;
import com.fasterxml.jackson.jr.ob.api.CollectionBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CollectionBuilderDynamicAccessTest {
    @Test
    void createsEmptyTypedArraysDirectly() throws Exception {
        CollectionBuilder builder = baseArrayMethodBuilder();
        Class<ArrayElement> elementType = runtimeArrayElementType();

        Object[] values = builder.emptyArray(elementType);

        assertThat(values).isEmpty();
        assertThat(values.getClass().getComponentType()).isSameAs(elementType);
    }

    @Test
    void createsSingletonTypedArraysDirectly() throws Exception {
        CollectionBuilder builder = baseArrayMethodBuilder();
        Class<ArrayElement> elementType = runtimeArrayElementType();

        Object[] values = builder.singletonArray(elementType, new ArrayElement("solo"));

        assertThat(values).singleElement().isInstanceOf(ArrayElement.class);
        assertThat(((ArrayElement) values[0]).name).isEqualTo("solo");
        assertThat(values.getClass().getComponentType()).isSameAs(elementType);
    }

    @Test
    void createsMultiValueTypedArraysDirectly() throws Exception {
        CollectionBuilder builder = baseArrayMethodBuilder();
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
    void readsEmptyTypedArraysThroughConfiguredJsonApi() throws Exception {
        BaseArrayMethodBuilder builder = baseArrayMethodBuilder();
        JSON json = jsonWithBaseArrayMethodBuilder(builder);
        Class<ArrayToken> elementType = ArrayToken.class;

        Object[] values = json.arrayOfFrom(elementType, "[]");

        assertThat(values).isEmpty();
        assertThat(values).isInstanceOf(ArrayToken[].class);
        assertThat(values.getClass().getComponentType()).isSameAs(elementType);
        assertThat(builder.invocationCounts.emptyArrayCalls).isEqualTo(1);
    }

    @Test
    void readsSingletonTypedArraysThroughConfiguredJsonApi() throws Exception {
        BaseArrayMethodBuilder builder = baseArrayMethodBuilder();
        JSON json = jsonWithBaseArrayMethodBuilder(builder);
        Class<ArrayToken> elementType = ArrayToken.class;

        Object[] values = json.arrayOfFrom(elementType, "[\"SOLO\"]");

        assertThat(values).containsExactly(ArrayToken.SOLO);
        assertThat(values).isInstanceOf(ArrayToken[].class);
        assertThat(values.getClass().getComponentType()).isSameAs(elementType);
        assertThat(builder.invocationCounts.singletonArrayCalls).isEqualTo(1);
    }

    @Test
    void readsMultiValueTypedArraysThroughConfiguredJsonApi() throws Exception {
        BaseArrayMethodBuilder builder = baseArrayMethodBuilder();
        JSON json = jsonWithBaseArrayMethodBuilder(builder);
        Class<ArrayToken> elementType = ArrayToken.class;

        Object[] values = json.arrayOfFrom(elementType, "[\"LEFT\",\"RIGHT\"]");

        assertThat(values).containsExactly(ArrayToken.LEFT, ArrayToken.RIGHT);
        assertThat(values).isInstanceOf(ArrayToken[].class);
        assertThat(values.getClass().getComponentType()).isSameAs(elementType);
        assertThat(builder.invocationCounts.buildArrayCalls).isEqualTo(1);
    }

    private static JSON jsonWithBaseArrayMethodBuilder(CollectionBuilder builder) {
        return JSON.builder()
                .collectionBuilder(builder)
                .build();
    }

    private static BaseArrayMethodBuilder baseArrayMethodBuilder() {
        return new BaseArrayMethodBuilder();
    }

    @SuppressWarnings("unchecked")
    private static Class<ArrayElement> runtimeArrayElementType() throws Exception {
        return (Class<ArrayElement>) JSON.std.beanFrom(Class.class, '"' + ArrayElement.class.getName() + '"');
    }

    private static final class BaseArrayMethodBuilder extends CollectionBuilder {
        private final InvocationCounts invocationCounts;
        private List<Object> currentValues;

        private BaseArrayMethodBuilder() {
            this(0, null, new InvocationCounts());
        }

        private BaseArrayMethodBuilder(int features, Class<?> collectionType, InvocationCounts invocationCounts) {
            super(features, collectionType);
            this.invocationCounts = invocationCounts;
        }

        @Override
        public CollectionBuilder newBuilder(int features) {
            return new BaseArrayMethodBuilder(features, null, invocationCounts);
        }

        @Override
        public CollectionBuilder newBuilder(Class<?> collectionType) {
            return new BaseArrayMethodBuilder(_features, collectionType, invocationCounts);
        }

        @Override
        public CollectionBuilder start() {
            currentValues = new ArrayList<>();
            return this;
        }

        @Override
        public CollectionBuilder add(Object value) {
            currentValues.add(value);
            return this;
        }

        @Override
        public Collection<Object> buildCollection() {
            Collection<Object> values = currentValues;
            currentValues = null;
            return values;
        }

        @Override
        public <T> T[] buildArray(Class<T> type) {
            invocationCounts.buildArrayCalls++;
            return super.buildArray(type);
        }

        @Override
        public <T> T[] emptyArray(Class<T> type) {
            invocationCounts.emptyArrayCalls++;
            return super.emptyArray(type);
        }

        @Override
        public <T> T[] singletonArray(Class<?> type, T value) {
            invocationCounts.singletonArrayCalls++;
            return super.singletonArray(type, value);
        }
    }

    private static final class InvocationCounts {
        private int buildArrayCalls;
        private int emptyArrayCalls;
        private int singletonArrayCalls;
    }

    public enum ArrayToken {
        LEFT,
        RIGHT,
        SOLO
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
