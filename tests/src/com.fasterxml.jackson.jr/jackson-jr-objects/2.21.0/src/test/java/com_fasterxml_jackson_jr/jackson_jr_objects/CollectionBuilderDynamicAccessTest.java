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

import java.util.ArrayList;
import java.util.Collection;

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

    @Test
    void readsPojoTypedArraysThroughJsonApiForEveryCardinality() throws Exception {
        Class<ArrayElement> elementType = runtimeArrayElementType();

        ArrayElement[] empty = JSON.std.arrayOfFrom(elementType, "[]");
        ArrayElement[] singleton = JSON.std.arrayOfFrom(elementType, "[{\"name\":\"solo\"}]");
        ArrayElement[] multiple = JSON.std.arrayOfFrom(elementType, "[{\"name\":\"left\"},{\"name\":\"right\"}]");

        assertThat(empty).isEmpty();
        assertThat(empty.getClass().getComponentType()).isSameAs(elementType);
        assertThat(singleton).extracting(value -> value.name).containsExactly("solo");
        assertThat(singleton.getClass().getComponentType()).isSameAs(elementType);
        assertThat(multiple).extracting(value -> value.name).containsExactly("left", "right");
        assertThat(multiple.getClass().getComponentType()).isSameAs(elementType);
    }

    @Test
    void typedArrayReadsUseConfiguredCollectionBuilderForEveryCardinality() throws Exception {
        RecordingCollectionBuilder builder = new RecordingCollectionBuilder();
        JSON json = JSON.builder()
                .collectionBuilder(builder)
                .build();
        Class<String> elementType = runtimeStringType();

        String[] empty = json.arrayOfFrom(elementType, "[]");
        String[] singleton = json.arrayOfFrom(elementType, "[\"solo\"]");
        String[] multiple = json.arrayOfFrom(elementType, "[\"left\",\"right\"]");

        assertThat(empty).isEmpty();
        assertThat(singleton).containsExactly("solo");
        assertThat(multiple).containsExactly("left", "right");
        assertThat(builder.counts.emptyArrayCalls).isEqualTo(1);
        assertThat(builder.counts.singletonArrayCalls).isEqualTo(1);
        assertThat(builder.counts.buildArrayCalls).isEqualTo(1);
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

    static final class RecordingCollectionBuilder extends CollectionBuilder {
        private final InvocationCounts counts;
        private Collection<Object> current;

        RecordingCollectionBuilder() {
            this(0, null, new InvocationCounts());
        }

        private RecordingCollectionBuilder(int features, Class<?> collectionType, InvocationCounts counts) {
            super(features, collectionType);
            this.counts = counts;
        }

        @Override
        public CollectionBuilder newBuilder(int features) {
            return new RecordingCollectionBuilder(features, _collectionType, counts);
        }

        @Override
        public CollectionBuilder newBuilder(Class<?> collectionType) {
            return new RecordingCollectionBuilder(_features, collectionType, counts);
        }

        @Override
        public CollectionBuilder start() {
            current = new ArrayList<>();
            return this;
        }

        @Override
        public CollectionBuilder add(Object value) {
            current.add(value);
            return this;
        }

        @Override
        public Collection<Object> buildCollection() {
            Collection<Object> result = current;
            current = null;
            return result;
        }

        @Override
        public <T> T[] buildArray(Class<T> type) {
            counts.buildArrayCalls++;
            return super.buildArray(type);
        }

        @Override
        public <T> T[] emptyArray(Class<T> type) {
            counts.emptyArrayCalls++;
            return super.emptyArray(type);
        }

        @Override
        public <T> T[] singletonArray(Class<?> type, T value) {
            counts.singletonArrayCalls++;
            return super.singletonArray(type, value);
        }
    }

    static final class InvocationCounts {
        private int buildArrayCalls;
        private int emptyArrayCalls;
        private int singletonArrayCalls;
    }
}
