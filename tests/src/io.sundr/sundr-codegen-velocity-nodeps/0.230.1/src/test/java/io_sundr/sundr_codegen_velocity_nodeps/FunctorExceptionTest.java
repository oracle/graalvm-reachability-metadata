/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;


import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.sundr.deps.org.apache.commons.collections.FunctorException;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;

public class FunctorExceptionTest {
    @Test
    void exercisesPublicApi() throws Exception {
        Throwable root = new IllegalArgumentException("root message");
        FunctorException exception = new FunctorException("operation", root);
        assertSame(root, exception.getCause());
        StringWriter output = new StringWriter();
        exception.printStackTrace(new PrintWriter(output));
        assertTrue(output.toString().contains("operation"));
        assertTrue(output.toString().contains("root message"));
    }
}
