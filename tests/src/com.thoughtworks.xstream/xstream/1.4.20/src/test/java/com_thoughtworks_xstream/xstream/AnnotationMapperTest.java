/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_xstream.xstream;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.XStreamer;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamImplicitCollection;
import com.thoughtworks.xstream.security.ExplicitTypePermission;
import com.thoughtworks.xstream.security.TypePermission;

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
    void serializesAndDeserializesXStreamWithProcessedAnnotationMapper() throws Exception {
        XStream xstream = newAnnotatedXStream();
        XStreamer streamer = new XStreamer();
        Inventory inventory = new Inventory(new Item("serialized configuration"));

        String xml = streamer.toXML(xstream, inventory);
        Object restored = streamer.fromXML(xml, permissionsForXStreamer());

        assertThat(xml).contains("com.thoughtworks.xstream.mapper.AnnotationMapper");
        assertThat(restored).isInstanceOfSatisfying(Inventory.class, value ->
            assertThat(value.getItemNames()).containsExactly("serialized configuration"));
    }

    @SuppressWarnings("deprecation")
    private static XStream newAnnotatedXStream() {
        XStream xstream = new XStream();
        xstream.allowTypes(new Class[]{Inventory.class, Item.class});
        xstream.processAnnotations(new Class[]{Inventory.class, Item.class});
        return xstream;
    }

    private static TypePermission[] permissionsForXStreamer() {
        TypePermission[] defaults = XStreamer.getDefaultPermissions();
        TypePermission[] permissions = Arrays.copyOf(defaults, defaults.length + 1);
        permissions[defaults.length] = new ExplicitTypePermission(new Class[]{Inventory.class, Item.class});
        return permissions;
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
