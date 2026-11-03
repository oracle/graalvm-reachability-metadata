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
import picocli.CommandLine.IParameterExceptionHandler;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises picocli's custom parameter-exception handling subsystem for actionable CLI validation.
public class ParameterExceptionHandlerTest {
    @Test
    public void formatsMissingRequiredOptionForTheApplication() {
        StringWriter output = new StringWriter();
        StringWriter error = new StringWriter();
        CommandLine commandLine = new CommandLine(new PublishCommand())
                .setParameterExceptionHandler(new FriendlyParameterExceptionHandler());
        commandLine.setOut(new PrintWriter(output));
        commandLine.setErr(new PrintWriter(error));

        int exitCode = commandLine.execute("release-2025");

        assertEquals(64, exitCode);
        assertEquals("", output.toString());
        assertTrue(
                error.toString().contains(
                        "invalid command: Missing required option: '--environment=<environment>'"));
        assertTrue(error.toString().contains("Usage: publish"));
        assertTrue(error.toString().contains("--environment=<environment>"));
    }

    @Command(name = "publish", description = "Publish a release to an environment.")
    static class PublishCommand implements Runnable {
        @Option(names = "--environment", required = true, description = "Deployment environment.")
        String environment;

        @Parameters(index = "0", description = "Release name.")
        String release;

        @Override
        public void run() {
            throw new AssertionError("invalid input must not publish a release");
        }
    }

    static class FriendlyParameterExceptionHandler implements IParameterExceptionHandler {
        @Override
        public int handleParseException(CommandLine.ParameterException exception, String[] args) {
            PrintWriter error = exception.getCommandLine().getErr();
            error.println("invalid command: " + exception.getMessage());
            exception.getCommandLine().usage(error);
            return 64;
        }
    }
}
