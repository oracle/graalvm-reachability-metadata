/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_jr.jackson_jr_objects;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.jr.ob.JSON;
import com.fasterxml.jackson.jr.ob.api.CollectionBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CollectionBuilderDynamicAccessTest {
    @Test
    void createsEmptyTypedArraysDirectly() throws Exception {
        CollectionBuilder builder = CollectionBuilder.defaultImpl();
        Class<ArrayElement> elementType = runtimeArrayElementType();

        ArrayElement[] values = builder.emptyArray(elementType);

        assertThat(values).isEmpty();
        assertThat(values).isInstanceOf(ArrayElement[].class);
        assertThat(values.getClass().getComponentType()).isSameAs(elementType);
    }

    @Test
    void createsSingletonTypedArraysDirectly() throws Exception {
        CollectionBuilder builder = CollectionBuilder.defaultImpl();
        Class<ArrayElement> elementType = runtimeArrayElementType();

        ArrayElement[] values = builder.singletonArray(elementType, new ArrayElement("solo"));

        assertThat(values).singleElement().isInstanceOf(ArrayElement.class);
        assertThat(values[0].name).isEqualTo("solo");
        assertThat(values).isInstanceOf(ArrayElement[].class);
        assertThat(values.getClass().getComponentType()).isSameAs(elementType);
    }

    @Test
    void createsMultiValueTypedArraysDirectly() throws Exception {
        CollectionBuilder builder = CollectionBuilder.defaultImpl();
        Class<ArrayElement> elementType = runtimeArrayElementType();

        ArrayElement[] values = builder.start()
                .add(new ArrayElement("left"))
                .add(new ArrayElement("right"))
                .buildArray(elementType);

        assertThat(values).hasSize(2);
        assertThat(values[0].name).isEqualTo("left");
        assertThat(values[1].name).isEqualTo("right");
        assertThat(values).isInstanceOf(ArrayElement[].class);
        assertThat(values.getClass().getComponentType()).isSameAs(elementType);
    }

    @Test
    void inheritedTypedArrayHelpersSupportCustomBuilders() throws Exception {
        CollectionBuilder builder = new RecordingCollectionBuilder();
        Class<ArrayElement> elementType = runtimeArrayElementType();
        ArrayElement solo = new ArrayElement("solo");

        ArrayElement[] emptyValues = builder.emptyArray(elementType);
        ArrayElement[] singletonValues = builder.singletonArray(elementType, solo);
        ArrayElement[] multipleValues = builder.start()
                .add(new ArrayElement("left"))
                .add(new ArrayElement("right"))
                .buildArray(elementType);

        assertThat(emptyValues).isEmpty();
        assertThat(emptyValues).isInstanceOf(ArrayElement[].class);
        assertThat(singletonValues).containsExactly(solo);
        assertThat(singletonValues).isInstanceOf(ArrayElement[].class);
        assertThat(multipleValues).hasSize(2);
        assertThat(multipleValues[0].name).isEqualTo("left");
        assertThat(multipleValues[1].name).isEqualTo("right");
        assertThat(multipleValues).isInstanceOf(ArrayElement[].class);
    }

    @Test
    void usesInheritedTypedArrayHelpersWhenJsonApiHasCustomBuilder() throws Exception {
        RecordingCollectionBuilder builder = new RecordingCollectionBuilder();
        JSON json = JSON.builder()
                .collectionBuilder(builder)
                .build();
        Class<String> elementType = runtimeStringType();

        String[] emptyValues = json.arrayOfFrom(elementType, "[]");
        String[] singletonValues = json.arrayOfFrom(elementType, "[\"solo\"]");
        String[] multipleValues = json.arrayOfFrom(elementType, "[\"left\",\"right\"]");

        assertThat(emptyValues).isEmpty();
        assertThat(emptyValues).isInstanceOf(String[].class);
        assertThat(singletonValues).containsExactly("solo");
        assertThat(singletonValues).isInstanceOf(String[].class);
        assertThat(multipleValues).containsExactly("left", "right");
        assertThat(multipleValues).isInstanceOf(String[].class);
        assertThat(builder.startedCollections.get()).isEqualTo(1);
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

    private static final class RecordingCollectionBuilder extends CollectionBuilder {
        private final AtomicInteger startedCollections;
        private Collection<Object> values;

        private RecordingCollectionBuilder() {
            this(0, null, new AtomicInteger());
        }

        private RecordingCollectionBuilder(int features, Class<?> collectionType, AtomicInteger startedCollections) {
            super(features, collectionType);
            this.startedCollections = startedCollections;
        }

        @Override
        public CollectionBuilder newBuilder(int features) {
            return new RecordingCollectionBuilder(features, _collectionType, startedCollections);
        }

        @Override
        public CollectionBuilder newBuilder(Class<?> collectionType) {
            return new RecordingCollectionBuilder(_features, collectionType, startedCollections);
        }

        @Override
        public CollectionBuilder start() {
            startedCollections.incrementAndGet();
            values = new ArrayList<>();
            return this;
        }

        @Override
        public CollectionBuilder add(Object value) {
            values.add(value);
            return this;
        }

        @Override
        public Collection<Object> buildCollection() {
            Collection<Object> result = values;
            values = null;
            return result;
        }
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
