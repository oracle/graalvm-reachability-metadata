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
import picocli.CommandLine.IExecutionExceptionHandler;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParseResult;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises picocli's execution exception handling subsystem for actionable CLI failures.
public class ExecutionExceptionHandlerTest {
    @Test
    public void translatesApplicationFailureIntoActionableExitResult() {
        StringWriter output = new StringWriter();
        StringWriter error = new StringWriter();
        CommandLine commandLine = new CommandLine(new SyncCommand())
                .setExecutionExceptionHandler(new FriendlyExecutionExceptionHandler());
        commandLine.setOut(new PrintWriter(output));
        commandLine.setErr(new PrintWriter(error));

        int exitCode = commandLine.execute("--warehouse", "warehouse-a");

        assertEquals(75, exitCode);
        assertEquals("", output.toString());
        assertTrue(error.toString().contains("unable to sync warehouse-a: warehouse unavailable"));
        assertTrue(error.toString().contains("command=sync"));
    }

    @Command(name = "sync", description = "Synchronize one warehouse.")
    static class SyncCommand implements Runnable {
        @Option(names = "--warehouse", required = true, description = "Warehouse to synchronize.")
        String warehouse;

        @Override
        public void run() {
            throw new IllegalStateException("warehouse unavailable");
        }
    }

    static class FriendlyExecutionExceptionHandler implements IExecutionExceptionHandler {
        @Override
        public int handleExecutionException(
                Exception exception, CommandLine commandLine, ParseResult parseResult) {
            PrintWriter error = commandLine.getErr();
            error.println("unable to sync " + parseResult.matchedOptionValue("--warehouse", "")
                    + ": " + exception.getMessage());
            error.println("command=" + parseResult.commandSpec().name());
            return 75;
        }
    }
}
