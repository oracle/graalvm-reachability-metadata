/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.sundr.deps.org.apache.commons.collections.functors.CloneTransformer;
import io.sundr.deps.org.apache.commons.lang.SerializationUtils;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

public class CloneTransformerTest {
    @Test
    void exercisesPublicApi() throws Exception {
        ArrayList<String> original = new ArrayList<>(List.of("seed"));
        ArrayList copy = (ArrayList) CloneTransformer.getInstance().transform(original);
        assertEquals(original, copy);
        assertNotSame(original, copy);
        copy.add("new");
        assertEquals(1, original.size());
    }

    @Test
    void rejectsSerializationWithoutExplicitOptIn() {
        UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class,
                () -> SerializationUtils.serialize((Serializable) CloneTransformer.getInstance()));
        assertTrue(exception.getMessage().contains(CloneTransformer.class.getName()));
        assertTrue(exception.getMessage().contains("disabled for security reasons"));
    }
}
