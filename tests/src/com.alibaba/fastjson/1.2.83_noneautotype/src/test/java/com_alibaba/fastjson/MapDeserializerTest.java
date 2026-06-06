/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.fastjson;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.fastjson.JSON;

import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;

public class MapDeserializerTest {
    @Test
    void parseObjectInstantiatesCustomMapType() {
        CustomMap value = JSON.parseObject("{\"first\":1,\"second\":2}", CustomMap.class);

        assertThat(value).isInstanceOf(CustomMap.class);
        assertThat(value).containsEntry("first", 1).containsEntry("second", 2);
    }

    public static class CustomMap extends LinkedHashMap<String, Integer> {
    }
}
