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

import io.sundr.deps.org.apache.commons.collections.Closure;
import io.sundr.deps.org.apache.commons.collections.functors.WhileClosure;
import io.sundr.deps.org.apache.commons.lang.SerializationUtils;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

public class WhileClosureTest {
    @Test
    void exercisesPublicApi() throws Exception {
        List<String> values = new ArrayList<>();
        Closure closure = WhileClosure.getInstance(value -> values.size() < 3, value -> values.add((String) value), false);
        closure.execute("item");
        assertEquals(List.of("item", "item", "item"), values);
    }

    @Test
    void rejectsSerializationWithoutExplicitOptIn() {
        List<String> values = new ArrayList<>();
        Closure closure = WhileClosure.getInstance(value -> values.size() < 2,
                value -> values.add((String) value), false);
        UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class,
                () -> SerializationUtils.serialize((Serializable) closure));
        assertTrue(exception.getMessage().contains(WhileClosure.class.getName()));
        assertTrue(exception.getMessage().contains("disabled for security reasons"));
    }
}
