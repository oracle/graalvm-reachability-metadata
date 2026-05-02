/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.CollectionFactory;
import org.springframework.util.ConcurrentReferenceHashMap;

public class CollectionFactoryTest {

    @Test
    void createsConcreteCollectionThroughDefaultConstructor() {
        Collection<String> collection = CollectionFactory.createCollection(ArrayDeque.class, 4);

        collection.add("first");
        collection.add("second");

        assertThat(collection).isInstanceOf(ArrayDeque.class);
        assertThat(collection).containsExactly("first", "second");
    }

    @Test
    void createsConcreteMapThroughDefaultConstructor() {
        Map<String, String> map = CollectionFactory.createMap(ConcurrentReferenceHashMap.class, 4);

        map.put("spring", "core");

        assertThat(map).isInstanceOf(ConcurrentReferenceHashMap.class);
        assertThat(map).containsEntry("spring", "core");
    }
}
