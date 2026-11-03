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
import picocli.CommandLine.IVersionProvider;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises picocli's version-provider subsystem for a release command.
public class VersionProviderCommandTest {
    @Test
    public void printsApplicationVersionWithoutRunningTheCommand() {
        StringWriter output = new StringWriter();
        StringWriter error = new StringWriter();
        CommandLine commandLine = new CommandLine(new ReleaseCommand());
        commandLine.setOut(new PrintWriter(output));
        commandLine.setErr(new PrintWriter(error));

        int exitCode = commandLine.execute("--version");

        assertEquals(CommandLine.ExitCode.OK, exitCode);
        assertEquals("orders-cli 4.7.7\n", output.toString());
        assertEquals("", error.toString());
    }

    @Command(
            name = "orders",
            versionProvider = ReleaseVersionProvider.class,
            mixinStandardHelpOptions = true)
    static class ReleaseCommand implements Runnable {
        @Override
        public void run() {
            throw new AssertionError("version requests must not execute the command");
        }
    }

    static class ReleaseVersionProvider implements IVersionProvider {
        @Override
        public String[] getVersion() {
            return new String[] {"orders-cli 4.7.7"};
        }
    }
}
