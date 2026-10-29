/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba_nacos.nacos_client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.alibaba.nacos.shaded.com.google.common.collect.ListMultimap;
import com.alibaba.nacos.shaded.com.google.common.collect.Multimaps;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

public class MultimapsInnerCustomListMultimapTest {
    @Test
    void restoresListFactoryAndBackingMap() throws Exception {
        ListMultimap<String, String> map = Multimaps.newListMultimap(new LinkedHashMap<>(), new MultimapFactories.Lists());
        map.put("group", "a");
        ListMultimap<String, String> restored = TestSupport.roundTrip(map);
        restored.put("group", "b");
        restored.put("other", "c");
        assertEquals(List.of("a", "b"), restored.get("group"));
        assertEquals(List.of("c"), restored.get("other"));
    }
}
