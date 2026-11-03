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

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises picocli's argument-file expansion for reusable command invocations.
public class ArgumentFileCommandTest {
    @Test
    public void expandsQuotedArgumentsAndCommentsFromArgumentFile() throws IOException {
        Path argumentFile = Files.createTempFile("picocli-export-", ".args");
        try {
            Files.writeString(argumentFile, "# nightly export\n--format\nCSV\n\"daily orders\"\n");

            StringWriter output = new StringWriter();
            StringWriter error = new StringWriter();
            CommandLine commandLine = new CommandLine(new ExportCommand());
            commandLine.setOut(new PrintWriter(output));
            commandLine.setErr(new PrintWriter(error));

            int exitCode = commandLine.execute("@" + argumentFile);

            assertEquals(CommandLine.ExitCode.OK, exitCode, error.toString());
            assertEquals("export=daily orders\nformat=CSV\n", output.toString());
            assertEquals("", error.toString());
        } finally {
            Files.deleteIfExists(argumentFile);
        }
    }

    @Command(name = "export")
    static class ExportCommand implements Runnable {
        @Spec
        CommandSpec spec;

        @Option(names = "--format", required = true)
        ExportFormat format;

        @Parameters(index = "0")
        String reportName;

        @Override
        public void run() {
            PrintWriter out = spec.commandLine().getOut();
            out.println("export=" + reportName);
            out.println("format=" + format);
        }
    }

    enum ExportFormat {
        CSV,
        JSON
    }
}
