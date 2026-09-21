/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_liquibase.liquibase_core;

import liquibase.command.core.StartH2CommandStep;
import org.h2.server.Service;
import org.junit.jupiter.api.Test;

import java.net.BindException;
import java.net.ServerSocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class StartH2CommandStepTest {

    @Test
    void startTcpServerInvokesH2ServerFactory() throws Exception {
        try (ServerSocket occupiedPort = new ServerSocket(0)) {
            int port = occupiedPort.getLocalPort();

            assertThatThrownBy(() -> StartH2CommandStepHarness.startTcpServerOn(port))
                    .hasRootCauseInstanceOf(BindException.class);
        }
    }

    @Test
    void startWebServerCreatesAndStartsH2WebService() throws Exception {
        Object webServer = StartH2CommandStepHarness.startWebServerOn(findAvailablePort());

        try {
            assertThat(webServer).isInstanceOf(Service.class);
            assertThat(((Service) webServer).getURL()).startsWith("http://");
        } finally {
            ((Service) webServer).stop();
        }
    }

    private static int findAvailablePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static final class StartH2CommandStepHarness extends StartH2CommandStep {
        private static void startTcpServerOn(int port) throws Exception {
            startTcpServer(port);
        }

        private static Object startWebServerOn(int port) throws Exception {
            return startWebServer(port);
        }
    }
}
