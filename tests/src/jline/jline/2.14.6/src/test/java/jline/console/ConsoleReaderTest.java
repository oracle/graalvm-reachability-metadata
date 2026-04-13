/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jline.console;

import jline.UnixTerminal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertSame;

public final class ConsoleReaderTest {

    private String previousSigcont;

    private String previousShell;

    private String previousStty;

    private Path tempDirectory;

    @BeforeEach
    void setUp() throws IOException {
        this.previousSigcont = System.getProperty("jline.sigcont");
        this.previousShell = System.getProperty("jline.sh");
        this.previousStty = System.getProperty("jline.stty");
        this.tempDirectory = Files.createTempDirectory("jline-console-reader-test");
    }

    @AfterEach
    void tearDown() throws IOException {
        restoreProperty("jline.sigcont", this.previousSigcont);
        restoreProperty("jline.sh", this.previousShell);
        restoreProperty("jline.stty", this.previousStty);
        if (this.tempDirectory != null) {
            Files.deleteIfExists(this.tempDirectory.resolve("fake-sh"));
            Files.deleteIfExists(this.tempDirectory);
            this.tempDirectory = null;
        }
    }

    @Test
    void constructorRegistersSigContHandlerForDefaultUnixTerminal() throws Exception {
        final Path fakeShell = createFakeShell();
        System.setProperty("jline.sh", fakeShell.toString());
        System.setProperty("jline.stty", fakeShell.toString());
        System.setProperty("jline.sigcont", Boolean.TRUE.toString());

        final UnixTerminal terminal = new UnixTerminal();
        final ConsoleReader consoleReader = new ConsoleReader(
            new ByteArrayInputStream(new byte[0]),
            new ByteArrayOutputStream(),
            terminal
        );
        try {
            assertSame(terminal, consoleReader.getTerminal());
        } finally {
            consoleReader.close();
        }
    }

    private Path createFakeShell() throws IOException {
        final Path fakeShell = this.tempDirectory.resolve("fake-sh");
        Files.writeString(
            fakeShell,
            "#!/bin/sh\n"
                + "if [ \"$1\" = \"-c\" ]; then\n"
                + "  cmd=\"$2\"\n"
                + "else\n"
                + "  cmd=\"stty $*\"\n"
                + "fi\n"
                + "case \"$cmd\" in\n"
                + "  *\" -g\"|*\" -g < /dev/tty\")\n"
                + "    printf '%s\\n' '0000:0000:0000:0000'\n"
                + "    ;;\n"
                + "  *\" -a\"|*\" -a < /dev/tty\")\n"
                + "    printf '%s\\n' 'speed 9600 baud; rows 24; columns 80; intr = ^C; lnext = ^V; -icanon -echo;'\n"
                + "    ;;\n"
                + "  *)\n"
                + "    exit 0\n"
                + "    ;;\n"
                + "esac\n",
            StandardCharsets.UTF_8
        );
        if (!fakeShell.toFile().setExecutable(true)) {
            throw new IOException("Failed to mark fake shell executable: " + fakeShell);
        }
        return fakeShell;
    }

    private static void restoreProperty(final String key, final String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }
}
