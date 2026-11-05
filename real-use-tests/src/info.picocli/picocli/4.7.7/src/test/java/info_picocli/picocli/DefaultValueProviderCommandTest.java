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
import picocli.CommandLine.IDefaultValueProvider;
import picocli.CommandLine.Model.ArgSpec;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises picocli's default value provider subsystem for profile-based CLI configuration.
public class DefaultValueProviderCommandTest {
    @Test
    public void suppliesProfileDefaultsWhileAllowingExplicitOverrides() {
        StringWriter output = new StringWriter();
        StringWriter error = new StringWriter();
        CommandLine commandLine = new CommandLine(new SyncCommand())
                .setDefaultValueProvider(new ProfileDefaults());
        commandLine.setOut(new PrintWriter(output));
        commandLine.setErr(new PrintWriter(error));

        int exitCode = commandLine.execute("warehouse-a", "--format", "csv");

        assertEquals(CommandLine.ExitCode.OK, exitCode, error.toString());
        assertEquals("warehouse=warehouse-a\nregion=us-east\nformat=csv\n", output.toString());
        assertEquals("", error.toString());
    }

    @Command(name = "sync", description = "Synchronize one warehouse.")
    static class SyncCommand implements Runnable {
        @Spec
        CommandSpec spec;

        @Option(names = "--region", required = true, description = "Warehouse region.")
        String region;

        @Option(names = "--format", required = true, description = "Output format.")
        String format;

        @Parameters(index = "0", description = "Warehouse name.")
        String warehouse;

        @Override
        public void run() {
            PrintWriter out = spec.commandLine().getOut();
            out.println("warehouse=" + warehouse);
            out.println("region=" + region);
            out.println("format=" + format);
        }
    }

    static class ProfileDefaults implements IDefaultValueProvider {
        private final Map<String, String> values = Map.of("--region", "us-east", "--format", "json");

        @Override
        public String defaultValue(ArgSpec argSpec) {
            if (argSpec instanceof OptionSpec) {
                return values.get(((OptionSpec) argSpec).longestName());
            }
            return null;
        }
    }
}
