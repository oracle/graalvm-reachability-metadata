/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;

import org.junit.jupiter.api.Test;
import org.springframework.core.CollectionFactory;

/** Verifies reflective creation of concrete collection and map types. */
public class CollectionFactoryTest {
    @Test
    void createsRequestedConcreteTypes() {
        ArrayList<String> collection = CollectionFactory.createCollection(ArrayList.class, 4);
        LinkedHashMap<String, Integer> map = CollectionFactory.createMap(LinkedHashMap.class, 4);

        collection.add("spring");
        map.put("core", 1);
        assertThat(collection).containsExactly("spring");
        assertThat(map).containsEntry("core", 1);
    }
}
