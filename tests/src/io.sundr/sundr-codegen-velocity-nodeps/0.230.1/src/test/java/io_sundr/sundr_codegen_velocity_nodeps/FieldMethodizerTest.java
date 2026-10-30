/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.sundr.deps.org.apache.velocity.app.FieldMethodizer;
import org.junit.jupiter.api.Test;

public class FieldMethodizerTest {
    @Test
    void exercisesPublicApi() throws Exception {
        FieldMethodizer fields = new FieldMethodizer(new Constants());
        assertEquals("greeting", fields.get("TITLE"));
        assertEquals(7, fields.get("COUNT"));
        assertNull(fields.get("instance"));
    }

    public static class Constants {
        public static final String TITLE = "greeting";
        public static final int COUNT = 7;
        public String instance = "not static";
    }
}
