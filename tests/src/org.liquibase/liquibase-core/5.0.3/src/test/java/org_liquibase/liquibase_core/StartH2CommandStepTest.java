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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.BindException;
import java.net.ServerSocket;
import java.sql.Connection;
import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class StartH2CommandStepTest {

    @Test
    void startTcpServerInvokesH2FactoryAndReportsOccupiedPort() throws Exception {
        try (ServerSocket occupiedPort = new ServerSocket(0)) {
            final int port = occupiedPort.getLocalPort();

            assertThatThrownBy(() -> StartH2CommandStepHarness.startTcpServerOn(port))
                    .hasRootCauseInstanceOf(BindException.class);
        }
    }

    @Test
    void startWebServerCreatesSessionsAndHandlesBrowserLaunchFailure() throws Throwable {
        final int port = findAvailablePort();
        final Object webServer = StartH2CommandStepHarness.startWebServerOn(port);
        final MethodHandle createWebSession = createWebSessionHandle();
        final String previousBrowser = System.setProperty("h2.browser", "start-h2-test-browser-%url");

        try (Connection devConnection = DriverManager.getConnection(
                "jdbc:h2:mem:startH2Dev",
                "dbuser",
                "letmein"
        ); Connection integrationConnection = DriverManager.getConnection(
                "jdbc:h2:mem:startH2Integration",
                "dbuser",
                "letmein"
        )) {
            final String devUrl = (String) createWebSession.invoke(devConnection, webServer, true);
            final String integrationUrl = (String) createWebSession.invoke(integrationConnection, webServer, false);

            assertThat(devUrl).startsWith("http://").contains("jsessionid=");
            assertThat(integrationUrl).startsWith("http://").contains("jsessionid=");
            assertThat(integrationUrl).isNotEqualTo(devUrl);
        } finally {
            try {
                ((Service) webServer).stop();
            } finally {
                restoreProperty("h2.browser", previousBrowser);
            }
        }
    }

    private static MethodHandle createWebSessionHandle() throws ReflectiveOperationException {
        return MethodHandles.privateLookupIn(StartH2CommandStep.class, MethodHandles.lookup())
                .findStatic(
                        StartH2CommandStep.class,
                        "createWebSession",
                        MethodType.methodType(String.class, Connection.class, Object.class, boolean.class)
                );
    }

    private static int findAvailablePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static void restoreProperty(String name, String previousValue) {
        if (previousValue == null) {
            System.clearProperty(name);
            return;
        }
        System.setProperty(name, previousValue);
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
