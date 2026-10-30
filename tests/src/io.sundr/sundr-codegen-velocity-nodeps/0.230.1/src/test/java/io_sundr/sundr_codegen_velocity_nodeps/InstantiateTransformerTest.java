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

import io.sundr.deps.org.apache.commons.collections.Transformer;
import io.sundr.deps.org.apache.commons.collections.functors.InstantiateTransformer;
import io.sundr.deps.org.apache.commons.lang.SerializationUtils;
import java.io.Serializable;
import org.junit.jupiter.api.Test;

public class InstantiateTransformerTest {
    @Test
    void exercisesPublicApi() throws Exception {
        Transformer transformer = InstantiateTransformer.getInstance(new Class<?>[]{String.class}, new Object[]{"seed"});
        assertEquals("seed", transformer.transform(StringBuilder.class).toString());
    }

    @Test
    void rejectsSerializationWithoutExplicitOptIn() {
        Transformer transformer = InstantiateTransformer.getInstance(null, null);
        UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class,
                () -> SerializationUtils.serialize((Serializable) transformer));
        assertTrue(exception.getMessage().contains(InstantiateTransformer.class.getName()));
        assertTrue(exception.getMessage().contains("disabled for security reasons"));
    }
}
