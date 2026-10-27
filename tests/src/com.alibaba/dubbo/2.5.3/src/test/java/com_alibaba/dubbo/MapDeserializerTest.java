/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.dubbo;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.dubbo.common.utils.LRUCache;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class MapDeserializerTest {
    @Test
    void preservesConcreteMapTypeAndEntries() throws Exception {
        LinkedHashMap<String, Integer> source = new LinkedHashMap<String, Integer>();
        source.put("first", 1);
        source.put("second", 2);

        Object copy = HessianTestSupport.roundTrip(source);
        PublicMap custom = new PublicMap();
        custom.put("custom", 3);
        Object customCopy = HessianTestSupport.roundTrip(custom);

        assertThat(copy).isInstanceOf(LinkedHashMap.class);
        assertThat(copy).isEqualTo(source);
        assertThat(customCopy).isInstanceOf(PublicMap.class);
        assertThat(customCopy).isEqualTo(custom);
    }

    @Test
    void readsObjectFieldsIntoConcreteMapType() throws Exception {
        ObjectFields source = new ObjectFields();
        source.first = 5;
        source.second = 8;

        Object copy = HessianTestSupport.roundTrip(source, LRUCache.class);
        Map<String, Integer> expected = new LinkedHashMap<String, Integer>();
        expected.put("first", 5);
        expected.put("second", 8);

        assertThat(copy).isInstanceOf(LRUCache.class).isEqualTo(expected);
    }

    public static class PublicMap extends LinkedHashMap<String, Integer> {
        public PublicMap() { }
    }

    public static class ObjectFields {
        public int first;
        public int second;
    }
}
