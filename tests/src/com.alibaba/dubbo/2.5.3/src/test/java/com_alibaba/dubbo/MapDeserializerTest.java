/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.dubbo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;

public class MapDeserializerTest {
    @Test
    void preservesConcreteMapTypeAndEntries() throws Exception {
        LinkedHashMap<String, Integer> source = new LinkedHashMap<String, Integer>();
        source.put("first", 1);
        source.put("second", 2);

        Object copy = HessianTestSupport.roundTrip(source);

        assertThat(copy).isInstanceOf(LinkedHashMap.class);
        assertThat(copy).isEqualTo(source);
    }
}
