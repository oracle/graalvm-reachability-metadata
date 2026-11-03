/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package info_picocli.picocli;

import org.junit.jupiter.api.Test;
import picocli.AutoComplete;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises picocli's shell-completion generation subsystem for an operations CLI.
public class ShellCompletionTest {
    @Test
    public void generatesBashCompletionForCommandHierarchy() {
        String completionScript = AutoComplete.bash("orders", new CommandLine(new OrdersCommand()));

        assertTrue(completionScript.contains("complete -F _complete_orders -o default orders"));
        assertTrue(completionScript.contains("deploy"));
        assertTrue(completionScript.contains("--environment"));
        assertTrue(completionScript.contains("--region"));
        assertTrue(completionScript.contains("--dry-run"));
    }

    @Command(name = "orders", subcommands = DeployCommand.class)
    static class OrdersCommand implements Runnable {
        @Override
        public void run() {
            // The application exposes its work through subcommands.
        }
    }

    @Command(name = "deploy", description = "Deploy an order service.")
    static class DeployCommand implements Runnable {
        @Option(names = "--environment", required = true)
        String environment;

        @Option(names = "--region", defaultValue = "us-east")
        String region;

        @Option(names = "--dry-run")
        boolean dryRun;

        @Parameters(index = "0")
        String service;

        @Override
        public void run() {
            // The application deploys the selected service here.
        }
    }
}
