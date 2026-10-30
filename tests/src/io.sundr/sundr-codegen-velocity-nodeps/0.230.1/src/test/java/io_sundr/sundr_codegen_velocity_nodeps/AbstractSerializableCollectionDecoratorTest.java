/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.sundr.deps.org.apache.commons.collections.collection.PredicatedCollection;
import io.sundr.deps.org.apache.commons.collections.functors.NotNullPredicate;
import io.sundr.deps.org.apache.commons.lang.SerializationUtils;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import org.junit.jupiter.api.Test;

public class AbstractSerializableCollectionDecoratorTest {
    @Test
    void exercisesPublicApi() throws Exception {
        Collection original = PredicatedCollection.decorate(new ArrayList<>(), NotNullPredicate.INSTANCE);
        original.add("value");
        Collection restored = (Collection) SerializationUtils.clone((Serializable) original);
        assertTrue(restored.contains("value"));
        assertThrows(IllegalArgumentException.class, () -> restored.add(null));
        restored.add("another");
        assertEquals(2, restored.size());
        assertEquals(1, original.size());
    }
}
