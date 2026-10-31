/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_modulith.spring_modulith_runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.AbstractMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.runtime.jsonpath.TypeRef;
import org.springframework.modulith.runtime.minidev.asm.BeansAccess;
import org.springframework.modulith.runtime.minidev.json.parser.JSONParser;
import org.springframework.modulith.runtime.minidev.json.writer.JsonReader;
import org.springframework.modulith.runtime.minidev.json.writer.JsonReaderI;

public class CollectionMapperInnerMapTypeTest {
    @Test
    void parsesObjectIntoParameterizedMap() throws Exception {
        JsonReader reader = new JsonReader();
        JsonReaderI<ModuleMap<String, String>> mapper = reader.getMapper(
                new TypeRef<ModuleMap<String, String>>() { }.getType());

        ModuleMap<String, String> modules = new JSONParser(JSONParser.MODE_RFC4627).parse("""
                {"inventory":"active","billing":"pending","catalog":null}
                """, mapper);

        assertThat(modules).isInstanceOf(ModuleMap.class)
                .hasSize(3)
                .containsEntry("inventory", "active")
                .containsEntry("billing", "pending")
                .containsEntry("catalog", null);
        assertThat(modules.keySet()).containsExactly("inventory", "billing", "catalog");
    }

    @Test
    void createsIndependentMapsWhenReusingMapper() throws Exception {
        JsonReader reader = new JsonReader();
        JsonReaderI<ModuleMap<String, String>> mapper = reader.getMapper(
                new TypeRef<ModuleMap<String, String>>() { }.getType());
        JSONParser parser = new JSONParser(JSONParser.MODE_RFC4627);

        ModuleMap<String, String> populated = parser.parse("""
                {"inventory":"active"}
                """, mapper);
        ModuleMap<String, String> empty = parser.parse("{}", mapper);

        assertThat(empty).isInstanceOf(ModuleMap.class).isNotSameAs(populated).isEmpty();
        assertThat(populated).hasSize(1).containsEntry("inventory", "active");
    }

    public static class ModuleMap<K, V> extends AbstractMap<K, V> {
        private final transient Map<K, V> entries = new LinkedHashMap<>();

        @Override
        public Set<Entry<K, V>> entrySet() {
            return entries.entrySet();
        }

        @Override
        public V put(K key, V value) {
            return entries.put(key, value);
        }
    }

    // The compiled accessor avoids runtime bytecode generation for this map with no bean properties.
    public static class ModuleMapAccAccess extends BeansAccess<ModuleMap<?, ?>> {
        @Override
        public void set(ModuleMap<?, ?> map, int index, Object value) {
            throw new IllegalArgumentException("Unknown property index: " + index);
        }

        @Override
        public Object get(ModuleMap<?, ?> map, int index) {
            throw new IllegalArgumentException("Unknown property index: " + index);
        }

        @Override
        public ModuleMap<?, ?> newInstance() {
            return new ModuleMap<>();
        }
    }
}
