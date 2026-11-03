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
import picocli.CommandLine.Parameters;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises picocli's context-sensitive help generation for a nested inventory command.
public class InventoryHelpTest {
    @Test
    public void rendersHelpForNestedSyncCommand() {
        StringWriter output = new StringWriter();
        StringWriter error = new StringWriter();
        CommandLine commandLine = new CommandLine(new InventoryCommand());
        commandLine.setOut(new PrintWriter(output));
        commandLine.setErr(new PrintWriter(error));

        int exitCode = commandLine.execute("sync", "--help");

        assertEquals(CommandLine.ExitCode.OK, exitCode);
        assertTrue(output.toString().contains("Usage: inventory sync"));
        assertTrue(output.toString().contains("--region=<region>"));
        assertTrue(output.toString().contains("--dry-run"));
        assertTrue(output.toString().contains("-h, --help"));
        assertEquals("", error.toString());
    }

    @Command(
            name = "inventory",
            description = "Synchronize inventory data.",
            subcommands = SyncCommand.class)
    static class InventoryCommand implements Runnable {
        @Override
        public void run() {
            // The application performs work through its subcommands.
        }
    }

    @Command(
            name = "sync",
            description = "Synchronize one warehouse.",
            mixinStandardHelpOptions = true)
    static class SyncCommand implements Runnable {
        @Option(names = "--region", description = "Warehouse region.", defaultValue = "us-east")
        String region;

        @Option(names = "--dry-run", description = "Report changes without applying them.")
        boolean dryRun;

        @Parameters(index = "0", description = "Warehouse name.")
        String warehouse;

        @Override
        public void run() {
            // The application synchronizes the selected warehouse here.
        }
    }
}
