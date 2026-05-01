/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity_dep;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.RuntimeLogger;
import org.apache.velocity.runtime.log.NullLogSystem;
import org.apache.velocity.util.introspection.Info;
import org.apache.velocity.util.introspection.UberspectImpl;
import org.apache.velocity.util.introspection.VelPropertySet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class UberspectImplTest {
    @Test
    void templateMapPropertyAssignmentUsesUberspectMapSetterFallback() throws Exception {
        VelocityEngine engine = new VelocityEngine();
        engine.setProperty(RuntimeConstants.RUNTIME_LOG_LOGSYSTEM, new NullLogSystem());
        engine.init();
        Map<String, Object> values = new HashMap<>();
        VelocityContext context = new VelocityContext();
        context.put("values", values);
        StringWriter writer = new StringWriter();

        boolean rendered = engine.evaluate(
                context,
                writer,
                "uberspect-map-property-assignment.vm",
                "#set($values.answer = \"forty-two\")$values.answer");

        assertTrue(rendered);
        assertEquals("forty-two", writer.toString());
        assertEquals("forty-two", values.get("answer"));
    }

    @Test
    void mapPropertySetterUsesPutWhenBeanSetterIsUnavailable() throws Exception {
        UberspectImpl uberspect = new UberspectImpl();
        uberspect.setRuntimeLogger(new NoOpRuntimeLogger());
        Map<String, Object> values = new HashMap<>();
        Info info = new Info("map-property-setter", 1, 1);

        VelPropertySet setter = uberspect.getPropertySet(values, "answer", "initial", info);

        assertNotNull(setter);
        assertEquals("put", setter.getMethodName());
        assertNull(setter.invoke(values, "forty-two"));
        assertEquals("forty-two", values.get("answer"));
        assertEquals("forty-two", setter.invoke(values, "forty-three"));
        assertEquals("forty-three", values.get("answer"));
    }

    private static class NoOpRuntimeLogger implements RuntimeLogger {
        @Override
        public void warn(Object message) {
        }

        @Override
        public void info(Object message) {
        }

        @Override
        public void error(Object message) {
        }

        @Override
        public void debug(Object message) {
        }
    }
}
