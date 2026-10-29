/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba_nacos.nacos_client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.alibaba.nacos.shaded.com.google.common.collect.SortedSetMultimap;
import com.alibaba.nacos.shaded.com.google.common.collect.Multimaps;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;

public class MultimapsInnerCustomSortedSetMultimapTest {
    @Test
    void restoresSortedFactoryForExistingAndNewKeys() throws Exception {
        SortedSetMultimap<String, String> map = Multimaps.newSortedSetMultimap(new LinkedHashMap<>(), new MultimapFactories.SortedSets());
        map.put("group", "b");
        map.put("group", "a");
        SortedSetMultimap<String, String> restored = TestSupport.roundTrip(map);
        assertEquals("a", restored.get("group").first());
        restored.put("new", "d");
        restored.put("new", "c");
        assertEquals("c", restored.get("new").first());
    }
}
