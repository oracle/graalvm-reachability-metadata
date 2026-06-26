/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_1_2_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import org.apache.log4j.jmx.Agent;
import org.junit.jupiter.api.Test;

public class AgentTest {

    @Test
    void startsServerObjectByLookingUpPublicStartMethod() throws Throwable {
        StartableServer server = new StartableServer();
        MethodHandle startServer = MethodHandles.privateLookupIn(Agent.class, MethodHandles.lookup())
                .findStatic(Agent.class, "startServer", MethodType.methodType(void.class, Object.class));

        startServer.invoke(server);

        assertThat(server.isStarted()).isTrue();
    }

    public static final class StartableServer {
        private boolean started;

        public void start() {
            started = true;
        }

        private boolean isStarted() {
            return started;
        }
    }
}
