/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;


import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import io.sundr.deps.org.apache.commons.lang.exception.NestableException;
import org.junit.jupiter.api.Test;

public class NestableDelegateTest {
    @Test
    void exercisesPublicApi() throws Exception {
        Throwable root = new IllegalArgumentException("root");
        NestableException exception = new NestableException("outer", root);
        assertArrayEquals(new String[]{"outer", "root"}, exception.getMessages());
        assertEquals("root", exception.getMessage(1));
        assertSame(root, exception.getThrowable(1));
        assertEquals(2, exception.getThrowableCount());
    }
}
