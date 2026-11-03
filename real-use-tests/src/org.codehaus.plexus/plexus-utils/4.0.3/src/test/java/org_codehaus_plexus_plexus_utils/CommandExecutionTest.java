/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus_plexus_utils;

import java.io.File;
import java.io.StringWriter;

import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.WriterStreamConsumer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Executes an external tool and captures its diagnostic output with Plexus command-line utilities.
 *
 * A build application invokes the Java launcher to validate a tool installation before starting a
 * build, and reports the launcher's version output to the operator.
 */
class CommandExecutionTest {

    @Test
    void validatesJavaLauncherAndCapturesVersionOutput() throws Exception {
        Commandline commandline = new Commandline(javaExecutable());
        commandline.createArg().setValue("-version");
        StringWriter standardOutput = new StringWriter();
        StringWriter errorOutput = new StringWriter();

        int exitCode = CommandLineUtils.executeCommandLine(
                commandline,
                new WriterStreamConsumer(standardOutput),
                new WriterStreamConsumer(errorOutput));

        assertThat(exitCode).isZero();
        assertThat(standardOutput.toString()).isEmpty();
        assertThat(errorOutput.toString()).contains("version");
    }

    private String javaExecutable() {
        String executable = System.getProperty("java.home") + File.separator + "bin" + File.separator
                + "java";
        if (System.getProperty("os.name").startsWith("Windows")) {
            executable += ".exe";
        }
        return executable;
    }
}
