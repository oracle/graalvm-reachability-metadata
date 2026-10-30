/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.sundr.deps.org.apache.commons.lang.enums.Enum;
import io.sundr.deps.org.apache.commons.lang.enums.EnumUtils;
import org.junit.jupiter.api.Test;

public class CommonsEnumTest {
    @Test
    void exercisesPublicApi() throws Exception {
        assertEquals("fast", Mode.FAST.getName());
        assertSame(Mode.FAST, EnumUtils.getEnum(Mode.class, "fast"));
        assertEquals(2, EnumUtils.getEnumList(Mode.class).size());
        assertTrue(Mode.FAST.compareTo(Mode.SLOW) < 0);
    }

    public static class Mode extends Enum {
        public static final Mode FAST = new Mode("fast");
        public static final Mode SLOW = new Mode("slow");

        private Mode(String name) {
            super(name);
        }
    }
}
