/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package info_picocli.picocli;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises picocli's exit-code exception mapping subsystem for an operational CLI.
public class ExitCodeMappingCommandTest {
    @Test
    public void mapsOperationalFailureToApplicationExitCode() {
        StringWriter output = new StringWriter();
        StringWriter error = new StringWriter();
        CommandLine commandLine = new CommandLine(new DeployCommand())
                .setExitCodeExceptionMapper(new OperationalFailureMapper());
        commandLine.setOut(new PrintWriter(output));
        commandLine.setErr(new PrintWriter(error));

        int exitCode = commandLine.execute("--environment", "production", "--service", "billing");

        assertEquals(75, exitCode);
        assertEquals("", output.toString());
        assertTrue(error.toString().contains("deployment service unavailable"));
    }

    @Command(name = "deploy")
    static class DeployCommand implements Runnable {
        @Option(names = "--environment", required = true)
        String environment;

        @Option(names = "--service", required = true)
        String service;

        @Override
        public void run() {
            throw new IllegalStateException("deployment service unavailable: " + service
                    + " in " + environment);
        }
    }

    static class OperationalFailureMapper implements CommandLine.IExitCodeExceptionMapper {
        @Override
        public int getExitCode(Throwable exception) {
            return exception instanceof IllegalStateException ? 75 : 1;
        }
    }
}
