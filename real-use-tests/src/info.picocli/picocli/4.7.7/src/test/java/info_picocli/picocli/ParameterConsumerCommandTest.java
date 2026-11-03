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
import picocli.CommandLine.IParameterConsumer;
import picocli.CommandLine.Model.ArgSpec;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises picocli's custom parameter-consumer subsystem for repeated configuration assignments.
public class ParameterConsumerCommandTest {
    @Test
    public void consumesRepeatedAssignmentsAsOneApplicationOption() {
        StringWriter output = new StringWriter();
        StringWriter error = new StringWriter();
        CommandLine commandLine = new CommandLine(new ConfigureCommand());
        commandLine.setOut(new PrintWriter(output));
        commandLine.setErr(new PrintWriter(error));

        int exitCode = commandLine.execute(
                "--set", "region=eu-west", "--set", "retries=3", "--set", "dryRun=true");

        assertEquals(CommandLine.ExitCode.OK, exitCode, error.toString());
        assertEquals("region=eu-west\nretries=3\ndryRun=true\n", output.toString());
        assertEquals("", error.toString());
    }

    @Test
    public void rejectsAssignmentWithoutASeparator() {
        StringWriter error = new StringWriter();
        CommandLine commandLine = new CommandLine(new ConfigureCommand());
        commandLine.setErr(new PrintWriter(error));

        int exitCode = commandLine.execute("--set", "region");

        assertEquals(CommandLine.ExitCode.USAGE, exitCode);
        assertTrue(error.toString().contains("key=value"));
    }

    @Command(name = "configure", description = "Configure a service from repeated key-value assignments.")
    static class ConfigureCommand implements Runnable {
        @Spec
        CommandSpec spec;

        @Option(
                names = "--set",
                parameterConsumer = AssignmentConsumer.class,
                description = "Set one configuration key to a value.")
        List<String> assignments = new ArrayList<>();

        @Override
        public void run() {
            PrintWriter out = spec.commandLine().getOut();
            for (String assignment : assignments) {
                out.println(assignment);
            }
        }
    }

    static class AssignmentConsumer implements IParameterConsumer {
        @Override
        public void consumeParameters(Stack<String> args, ArgSpec argSpec, CommandSpec commandSpec) {
            String assignment = args.pop();
            if (!assignment.contains("=")) {
                throw new CommandLine.ParameterException(
                        commandSpec.commandLine(), "Assignment must use key=value: " + assignment);
            }
            List<String> assignments = argSpec.getValue();
            assignments.add(assignment);
            argSpec.setValue(assignments);
        }
    }
}
