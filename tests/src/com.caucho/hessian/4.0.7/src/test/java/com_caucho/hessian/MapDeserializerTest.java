/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_caucho.hessian;

import com.caucho.hessian.io.BeanSerializer;
import com.caucho.hessian.io.MapDeserializer;
import com.caucho.hessian.io.SerializerFactory;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MapDeserializerTest {
    @Test
    void restoresMapEntriesAndConcreteType() throws Exception {
        LinkedHashMap<String, Integer> original = new LinkedHashMap<>();
        original.put("apples", 3);
        original.put("pears", 5);
        for (boolean hessian2 : new boolean[] {false, true}) {
            Map<String, Integer> restored = HessianStreams.roundTrip(original, new SerializerFactory(), hessian2);
            assertEquals(LinkedHashMap.class, restored.getClass());
            assertEquals(original, restored);
        }
    }

    @Test
    void decodesObjectPropertiesIntoMap() throws Exception {
        SerializerFactory factory = HessianStreams.codecs(Product.class,
                new BeanSerializer(Product.class, getClass().getClassLoader()), new MapDeserializer(LinkedHashMap.class));
        Object restored = HessianStreams.roundTrip((Object) new Product("crate"), factory, true);
        assertEquals(Map.of("name", "crate"), restored);
        assertEquals(LinkedHashMap.class, restored.getClass());
    }

    public static class Product {
        private String name;

        public Product(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
