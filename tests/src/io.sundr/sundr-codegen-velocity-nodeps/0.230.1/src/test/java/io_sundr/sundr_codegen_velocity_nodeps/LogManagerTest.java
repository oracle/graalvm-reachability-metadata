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

import io.sundr.deps.org.apache.velocity.VelocityContext;
import io.sundr.deps.org.apache.velocity.app.VelocityEngine;
import io.sundr.deps.org.apache.velocity.exception.VelocityException;
import io.sundr.deps.org.apache.velocity.runtime.log.SystemLogChute;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;

public class LogManagerTest {
    @Test
    void exercisesPublicApi() throws Exception {
        VelocityEngine engine = TestSupport.engine();
        engine.clearProperty("runtime.log.logsystem");
        engine.setProperty("runtime.log.logsystem.class", SystemLogChute.class.getName());
        engine.setProperty("runtime.log.logsystem.system.level", "error");
        engine.init();
        StringWriter output = new StringWriter();
        assertTrue(engine.evaluate(new VelocityContext(), output, "log-manager", "#set($n = 2 + 3)$n"));
        assertEquals("5", output.toString());
    }

    @Test
    void rejectsLoggerClassesThatDoNotImplementLogChute() {
        VelocityEngine engine = TestSupport.engine();
        engine.clearProperty("runtime.log.logsystem");
        engine.setProperty("runtime.log.logsystem.class", StringBuilder.class.getName());
        VelocityException exception = assertThrows(VelocityException.class, engine::init);
        assertTrue(exception.getMessage().contains(StringBuilder.class.getName()));
        VelocityException initialization = (VelocityException) exception.getWrappedThrowable();
        assertTrue(initialization.getWrappedThrowable().getMessage().contains("does not implement"));
    }
}
