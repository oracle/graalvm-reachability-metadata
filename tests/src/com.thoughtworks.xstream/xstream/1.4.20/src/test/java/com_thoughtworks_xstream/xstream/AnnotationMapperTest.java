/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_xstream.xstream;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamImplicitCollection;
import com.thoughtworks.xstream.core.ClassLoaderReference;
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
    void serializesAndDeserializesAnnotationMapperWithObjectStreams() throws Exception {
        AnnotationMapper mapper = newAnnotationMapper();
        XStream xstream = new XStream();
        xstream.allowTypesByWildcard(new String[]{"com.thoughtworks.xstream.**"});
        StringWriter writer = new StringWriter();

        try (ObjectOutputStream output = xstream.createObjectOutputStream(writer)) {
            output.writeObject(mapper);
        }
        String xml = writer.toString();

        Object restored;
        try (ObjectInputStream input = xstream.createObjectInputStream(new StringReader(xml))) {
            restored = input.readObject();
        }

        assertThat(xml).contains("com.thoughtworks.xstream.mapper.AnnotationMapper");
        assertThat(restored).isInstanceOf(AnnotationMapper.class);
    }

    @SuppressWarnings("deprecation")
    private static XStream newAnnotatedXStream() {
        XStream xstream = new XStream();
        xstream.allowTypes(new Class[]{Inventory.class, Item.class});
        xstream.processAnnotations(new Class[]{Inventory.class, Item.class});
        return xstream;
    }

    private static AnnotationMapper newAnnotationMapper() {
        ClassLoaderReference classLoaderReference = new ClassLoaderReference(
            AnnotationMapperTest.class.getClassLoader());
        return new AnnotationMapper(new DefaultMapper(classLoaderReference), null, null, classLoaderReference, null);
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
