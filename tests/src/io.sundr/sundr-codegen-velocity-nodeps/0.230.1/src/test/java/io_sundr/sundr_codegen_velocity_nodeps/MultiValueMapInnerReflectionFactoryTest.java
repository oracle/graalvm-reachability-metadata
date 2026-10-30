/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;


import static org.junit.jupiter.api.Assertions.assertEquals;

import io.sundr.deps.org.apache.commons.collections.map.MultiValueMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class MultiValueMapInnerReflectionFactoryTest {
    @Test
    void exercisesPublicApi() throws Exception {
        MultiValueMap map = MultiValueMap.decorate(new HashMap<>(), LinkedHashSet.class);
        map.put("key", "one");
        map.put("key", "two");
        map.put("key", "one");
        assertEquals(Set.of("one", "two"), map.getCollection("key"));
        assertEquals(2, map.totalSize());
    }
}
