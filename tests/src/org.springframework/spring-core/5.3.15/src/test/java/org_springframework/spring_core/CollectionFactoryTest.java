/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.CollectionFactory;

/** Verifies reflective creation of concrete collection and map implementations. */
public class CollectionFactoryTest {
    @Test
    void createsRequestedConcreteTypes() {
        Collection<String> collection = CollectionFactory.createCollection(CustomList.class, String.class, 4);
        Map<String, Integer> map = CollectionFactory.createMap(CustomMap.class, String.class, 4);

        collection.add("spring");
        map.put("core", 5);

        assertThat(collection).isInstanceOf(CustomList.class).containsExactly("spring");
        assertThat(map).isInstanceOf(CustomMap.class).containsEntry("core", 5);
    }

    public static final class CustomList extends ArrayList<String> {
        private static final long serialVersionUID = 1L;
    }

    public static final class CustomMap extends LinkedHashMap<String, Integer> {
        private static final long serialVersionUID = 1L;
    }
}
