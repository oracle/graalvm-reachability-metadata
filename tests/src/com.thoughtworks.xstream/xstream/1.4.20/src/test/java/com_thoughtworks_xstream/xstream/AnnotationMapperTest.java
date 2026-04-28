/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_xstream.xstream;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.NotActiveException;
import java.io.ObjectInputValidation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamImplicitCollection;
import com.thoughtworks.xstream.converters.reflection.SerializationMethodInvoker;
import com.thoughtworks.xstream.core.ClassLoaderReference;
import com.thoughtworks.xstream.core.util.CustomObjectInputStream;
import com.thoughtworks.xstream.core.util.CustomObjectOutputStream;
import com.thoughtworks.xstream.mapper.AnnotationMapper;
import com.thoughtworks.xstream.mapper.DefaultMapper;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AnnotationMapperTest {
    @Test
    @SuppressWarnings("deprecation")
    void processesClassLevelImplicitCollectionAnnotations() {
        XStream xstream = newAnnotatedXStream();
        Inventory inventory = new Inventory(new Item("adapter"), new Item("mapper"));

        String xml = xstream.toXML(inventory);
        Object restored = xstream.fromXML(xml);

        assertThat(xml).contains("<inventory>");
        assertThat(xml).contains("<item>");
        assertThat(xml).doesNotContain("<items>");
        assertThat(restored).isInstanceOfSatisfying(Inventory.class, value ->
            assertThat(value.getItemNames()).containsExactly("adapter", "mapper"));
    }

    @Test
    @SuppressWarnings("deprecation")
    void invokesCustomSerializationHooksForAnnotationMapper() throws Exception {
        ClassLoaderReference classLoaderReference = new ClassLoaderReference(
            AnnotationMapperTest.class.getClassLoader());
        AnnotationMapper mapper = newAnnotationMapper(classLoaderReference);
        RecordingOutputCallback outputCallback = new RecordingOutputCallback();
        RecordingInputCallback inputCallback = new RecordingInputCallback(classLoaderReference);
        SerializationMethodInvoker invoker = new SerializationMethodInvoker();

        invoker.callWriteObject(AnnotationMapper.class, mapper, new CustomObjectOutputStream(outputCallback));
        invoker.callReadObject(
            AnnotationMapper.class,
            mapper,
            new CustomObjectInputStream(inputCallback, classLoaderReference));

        assertThat(outputCallback.defaultObjectWritten).isTrue();
        assertThat(outputCallback.writtenObjects).contains(classLoaderReference);
        assertThat(inputCallback.defaultObjectRead).isTrue();
        assertThat(inputCallback.objectsRead).containsExactly(classLoaderReference);
    }

    @SuppressWarnings("deprecation")
    private static XStream newAnnotatedXStream() {
        XStream xstream = new XStream();
        xstream.allowTypes(new Class[]{Inventory.class, Item.class});
        xstream.processAnnotations(new Class[]{Inventory.class, Item.class});
        return xstream;
    }

    private static AnnotationMapper newAnnotationMapper(ClassLoaderReference classLoaderReference) {
        return new AnnotationMapper(new DefaultMapper(classLoaderReference), null, null, classLoaderReference, null);
    }

    private static final class RecordingOutputCallback implements CustomObjectOutputStream.StreamCallback {
        private final List<Object> writtenObjects = new ArrayList<>();
        private boolean defaultObjectWritten;

        @Override
        public void writeToStream(Object object) {
            writtenObjects.add(object);
        }

        @Override
        public void writeFieldsToStream(Map fields) {
            writtenObjects.add(fields);
        }

        @Override
        public void defaultWriteObject() {
            defaultObjectWritten = true;
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }

    private static final class RecordingInputCallback implements CustomObjectInputStream.StreamCallback {
        private final List<Object> objectsToRead = new ArrayList<>();
        private final List<Object> objectsRead = new ArrayList<>();
        private boolean defaultObjectRead;

        RecordingInputCallback(ClassLoaderReference classLoaderReference) {
            objectsToRead.add(1);
            objectsToRead.add(classLoaderReference);
        }

        @Override
        public Object readFromStream() {
            Object object = objectsToRead.remove(0);
            if (!(object instanceof Integer)) {
                objectsRead.add(object);
            }
            return object;
        }

        @Override
        public Map readFieldsFromStream() throws IOException {
            throw new IOException("readFields is not used by AnnotationMapper");
        }

        @Override
        public void defaultReadObject() {
            defaultObjectRead = true;
        }

        @Override
        public void registerValidation(ObjectInputValidation validation, int priority)
                throws NotActiveException, InvalidObjectException {
            throw new NotActiveException("validation is not used by AnnotationMapper");
        }

        @Override
        public void close() {
        }
    }

    @XStreamAlias("inventory")
    @XStreamImplicitCollection(value = "items", item = "item")
    @SuppressWarnings("deprecation")
    public static final class Inventory {
        private List<Item> items = new ArrayList<>();

        public Inventory() {
        }

        Inventory(Item... items) {
            this.items.addAll(Arrays.asList(items));
        }

        List<String> getItemNames() {
            List<String> names = new ArrayList<>();
            for (Item item : items) {
                names.add(item.name);
            }
            return names;
        }
    }

    public static final class Item {
        private String name;

        public Item() {
        }

        Item(String name) {
            this.name = name;
        }
    }
}
