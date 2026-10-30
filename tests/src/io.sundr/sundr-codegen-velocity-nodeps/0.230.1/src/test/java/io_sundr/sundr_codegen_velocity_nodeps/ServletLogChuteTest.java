/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.sundr.deps.org.apache.velocity.runtime.RuntimeInstance;
import io.sundr.deps.org.apache.velocity.runtime.log.LogChute;
import io.sundr.deps.org.apache.velocity.runtime.log.ServletLogChute;
import javax.servlet.ServletContext;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.junit.jupiter.api.Test;

public class ServletLogChuteTest {
    @Test
    void exercisesPublicApi() throws Exception {
        ContextHandler handler = new ContextHandler("/metadata");
        Server server = new Server();
        server.setHandler(handler);
        server.setStopTimeout(10000);
        try {
            server.start();
            RuntimeInstance runtime = new RuntimeInstance();
            runtime.setApplicationAttribute(ServletContext.class.getName(), handler.getServletContext());
            runtime.setProperty(ServletLogChute.RUNTIME_LOG_LEVEL_KEY, "warn");
            ServletLogChute chute = new ServletLogChute();
            chute.init(runtime);
            assertFalse(chute.isLevelEnabled(LogChute.INFO_ID));
            assertTrue(chute.isLevelEnabled(LogChute.WARN_ID));
            chute.log(LogChute.WARN_ID, "metadata test");
            assertEquals(LogChute.WARN_ID, chute.getEnabledLevel());
        } finally {
            server.stop();
            server.destroy();
        }
    }
}
