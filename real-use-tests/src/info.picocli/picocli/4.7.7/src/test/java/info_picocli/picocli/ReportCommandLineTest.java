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
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises picocli's subcommand dispatch and typed option parsing for a report command.
public class ReportCommandLineTest {
    @Test
    public void dispatchesReportSubcommandAndParsesOptions() {
        StringWriter output = new StringWriter();
        StringWriter error = new StringWriter();
        CommandLine commandLine = new CommandLine(new DeskCommand());
        commandLine.setOut(new PrintWriter(output));
        commandLine.setErr(new PrintWriter(error));

        int exitCode = commandLine.execute(
                "report", "--format", "CSV", "--tag", "critical,customer", "orders");

        assertEquals(0, exitCode, error.toString());
        assertEquals("report=orders\nformat=CSV\ntags=[critical, customer]\n", output.toString());
        assertEquals("", error.toString());
    }

    @Command(name = "desk", subcommands = ReportCommand.class)
    static class DeskCommand implements Runnable {
        @Override
        public void run() {
            // The application exposes its work through subcommands.
        }
    }

    @Command(name = "report")
    static class ReportCommand implements Runnable {
        @Spec
        CommandSpec spec;

        @Option(names = "--format", required = true)
        ReportFormat format;

        @Option(names = "--tag", split = ",")
        List<String> tags;

        @Parameters(index = "0")
        String reportName;

        @Override
        public void run() {
            PrintWriter out = spec.commandLine().getOut();
            out.println("report=" + reportName);
            out.println("format=" + format);
            out.println("tags=" + tags);
        }
    }

    enum ReportFormat {
        CSV,
        JSON
    }
}
