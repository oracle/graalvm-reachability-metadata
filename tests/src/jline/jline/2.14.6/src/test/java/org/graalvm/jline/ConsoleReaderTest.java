/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.jline;

import jline.UnixTerminal;
import jline.console.ConsoleReader;
import jline.internal.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class ConsoleReaderTest {

    private String originalSigCont;
    private String originalShellCommand;
    private String originalSttyCommand;
    private String originalUserHome;
    private Path temporaryDirectory;

    @BeforeEach
    void setUp() throws Exception {
        originalSigCont = System.getProperty("jline.sigcont");
        originalShellCommand = System.getProperty("jline.sh");
        originalSttyCommand = System.getProperty("jline.stty");
        originalUserHome = System.getProperty("user.home");

        temporaryDirectory = Files.createTempDirectory("jline-console-reader-");
        Path shellScript = writeFakeShell(temporaryDirectory.resolve("fake-sh"));

        System.setProperty("jline.sigcont", Boolean.TRUE.toString());
        System.setProperty("jline.sh", shellScript.toString());
        System.setProperty("jline.stty", "mock-stty");
        System.setProperty("user.home", temporaryDirectory.toString());
        Configuration.reset();
    }

    @AfterEach
    void tearDown() {
        restoreProperty("jline.sigcont", originalSigCont);
        restoreProperty("jline.sh", originalShellCommand);
        restoreProperty("jline.stty", originalSttyCommand);
        restoreProperty("user.home", originalUserHome);
        Configuration.reset();
    }

    @Test
    void constructorRegistersTheSigContHandlerWhenEnabledForTheDefaultUnixTerminal() throws Exception {
        assumeTrue(!System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win"));

        UnixTerminal terminal = new UnixTerminal();

        try (ConsoleReader reader = new ConsoleReader(
                "console-reader-test",
                new ByteArrayInputStream(new byte[0]),
                new ByteArrayOutputStream(),
                terminal)) {
            assertThat(reader.getTerminal()).isSameAs(terminal);
        }
    }

    private Path writeFakeShell(final Path shellScript) throws Exception {
        Files.writeString(shellScript, "#!/bin/sh\n"
                + "command=\"$2\"\n"
                + "case \"$command\" in\n"
                + "  *\"-g\"*)\n"
                + "    printf 'mock-terminal-state\\n'\n"
                + "    ;;\n"
                + "  *\"-a\"*)\n"
                + "    printf 'speed 9600 baud; 24 rows; 80 columns;\\nintr = ^C; lnext = ^V;\\n'\n"
                + "    ;;\n"
                + "  *)\n"
                + "    exit 0\n"
                + "    ;;\n"
                + "esac\n");
        assertThat(shellScript.toFile().setExecutable(true)).isTrue();
        return shellScript;
    }

    private static void restoreProperty(final String name, final String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }
}
