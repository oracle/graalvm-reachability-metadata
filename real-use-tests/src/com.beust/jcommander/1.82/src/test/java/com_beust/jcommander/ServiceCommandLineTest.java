/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_beust.jcommander;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.beust.jcommander.ParametersDelegate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises JCommander's delegated option groups for a service launch command.
public class ServiceCommandLineTest {
    @Test
    public void parsesOptionsOwnedByTheCommandAndItsDelegatedConfiguration() {
        ServiceOptions options = new ServiceOptions();
        JCommander commander = JCommander.newBuilder()
                .programName("service")
                .addObject(options)
                .build();

        commander.parse(
                "--port", "8080",
                "--endpoint", "https://api.example.test",
                "--tls");

        assertEquals(8080, options.port);
        assertEquals("https://api.example.test", options.connection.endpoint);
        assertTrue(options.connection.tls);
    }

    @Parameters(commandDescription = "Start the service")
    public static class ServiceOptions {
        @Parameter(names = "--port", description = "port on which the service listens", required = true)
        private int port;

        @ParametersDelegate
        private ConnectionOptions connection = new ConnectionOptions();
    }

    public static class ConnectionOptions {
        @Parameter(names = "--endpoint", description = "remote service endpoint", required = true)
        private String endpoint;

        @Parameter(names = "--tls", description = "use TLS for the remote connection")
        private boolean tls;
    }
}
