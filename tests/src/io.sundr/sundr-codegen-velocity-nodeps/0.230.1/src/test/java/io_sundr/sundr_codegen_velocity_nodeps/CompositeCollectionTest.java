/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;


import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import io.sundr.deps.org.apache.commons.collections.collection.CompositeCollection;
import java.util.List;
import org.junit.jupiter.api.Test;

public class CompositeCollectionTest {
    @Test
    void exercisesPublicApi() throws Exception {
        CompositeCollection collection = new CompositeCollection(List.of("one", "two"));
        collection.addComposited(List.of("three"));
        assertArrayEquals(new String[]{"one", "two", "three"}, collection.toArray(new String[0]));
    }
}
