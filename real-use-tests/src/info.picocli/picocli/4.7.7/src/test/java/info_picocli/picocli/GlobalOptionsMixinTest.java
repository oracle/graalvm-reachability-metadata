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
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises picocli's reusable mixins and parent-command wiring for global deployment options.
public class GlobalOptionsMixinTest {
    @Test
    public void passesGlobalOptionsFromRootCommandToSubcommand() {
        StringWriter output = new StringWriter();
        StringWriter error = new StringWriter();
        CommandLine commandLine = new CommandLine(new OperationsCommand());
        commandLine.setOut(new PrintWriter(output));
        commandLine.setErr(new PrintWriter(error));

        int exitCode = commandLine.execute("--verbose", "deploy", "orders", "--region", "eu-west");

        assertEquals(CommandLine.ExitCode.OK, exitCode, error.toString());
        assertEquals("deploy=orders\nregion=eu-west\nverbose=true\n", output.toString());
        assertEquals("", error.toString());
    }

    @Command(name = "operations", subcommands = DeployCommand.class)
    static class OperationsCommand implements Runnable {
        @Mixin
        GlobalOptions globalOptions;

        @Override
        public void run() {
            // The application exposes its work through subcommands.
        }
    }

    static class GlobalOptions {
        @Option(names = "--verbose", description = "Print detailed operation output.")
        boolean verbose;
    }

    @Command(name = "deploy")
    static class DeployCommand implements Runnable {
        @ParentCommand
        OperationsCommand parent;

        @Spec
        CommandSpec spec;

        @Option(names = "--region", required = true)
        String region;

        @Parameters(index = "0")
        String operation;

        @Override
        public void run() {
            PrintWriter out = spec.commandLine().getOut();
            out.println("deploy=" + operation);
            out.println("region=" + region);
            out.println("verbose=" + parent.globalOptions.verbose);
        }
    }
}
