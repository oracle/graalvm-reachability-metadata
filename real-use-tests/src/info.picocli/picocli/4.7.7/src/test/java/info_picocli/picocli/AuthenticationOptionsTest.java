/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package info_picocli.picocli;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises picocli's mutually exclusive argument groups for deployment credentials.
public class AuthenticationOptionsTest {
    @Test
    public void acceptsOneCredentialAndRejectsConflictingCredentials() {
        StringWriter output = new StringWriter();
        StringWriter error = new StringWriter();
        CommandLine commandLine = new CommandLine(new DeployCommand());
        commandLine.setOut(new PrintWriter(output));
        commandLine.setErr(new PrintWriter(error));

        int tokenExitCode = commandLine.execute("--service", "billing", "--token", "secret");

        assertEquals(CommandLine.ExitCode.OK, tokenExitCode, error.toString());
        assertEquals("deploy=billing\nauth=token:secret\n", output.toString());
        assertEquals("", error.toString());

        output.getBuffer().setLength(0);
        error.getBuffer().setLength(0);
        int conflictingExitCode = commandLine.execute(
                "--service", "billing", "--token", "secret", "--certificate", "client.pem");

        assertEquals(CommandLine.ExitCode.USAGE, conflictingExitCode);
        assertTrue(error.toString().contains("mutually exclusive"));
    }

    @Command(name = "deploy")
    static class DeployCommand implements Runnable {
        @Spec
        CommandSpec spec;

        @Option(names = "--service", required = true)
        String service;

        @ArgGroup(exclusive = true, multiplicity = "1")
        Credentials credentials;

        @Override
        public void run() {
            PrintWriter out = spec.commandLine().getOut();
            out.println("deploy=" + service);
            out.println("auth=" + credentials.describe());
        }
    }

    static class Credentials {
        @Option(names = "--token")
        String token;

        @Option(names = "--certificate")
        String certificate;

        String describe() {
            return token != null ? "token:" + token : "certificate:" + certificate;
        }
    }
}
