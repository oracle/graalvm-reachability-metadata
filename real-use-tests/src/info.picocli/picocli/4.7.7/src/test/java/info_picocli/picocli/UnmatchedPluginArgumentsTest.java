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
import picocli.CommandLine.Spec;
import picocli.CommandLine.Unmatched;
import picocli.CommandLine.Model.CommandSpec;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises picocli's unmatched argument capture subsystem for forwarding plugin options.
public class UnmatchedPluginArgumentsTest {
    @Test
    public void forwardsArgumentsNotOwnedByTheLauncher() {
        StringWriter output = new StringWriter();
        StringWriter error = new StringWriter();
        CommandLine commandLine = new CommandLine(new LauncherCommand())
                .setUnmatchedArgumentsAllowed(true);
        commandLine.setOut(new PrintWriter(output));
        commandLine.setErr(new PrintWriter(error));

        int exitCode = commandLine.execute(
                "--plugin", "metrics", "--verbose", "--sample-rate", "5", "orders");

        assertEquals(CommandLine.ExitCode.OK, exitCode, error.toString());
        assertEquals(
                "plugin=metrics\nforwarded=[--verbose, --sample-rate, 5, orders]\n",
                output.toString());
        assertEquals("", error.toString());
    }

    @Command(name = "launcher", description = "Launch a plugin with its remaining arguments.")
    static class LauncherCommand implements Runnable {
        @Spec
        CommandSpec spec;

        @Option(names = "--plugin", required = true, description = "Plugin to launch.")
        String plugin;

        @Unmatched
        List<String> forwarded = new ArrayList<>();

        @Override
        public void run() {
            PrintWriter out = spec.commandLine().getOut();
            out.println("plugin=" + plugin);
            out.println("forwarded=" + forwarded);
        }
    }
}
