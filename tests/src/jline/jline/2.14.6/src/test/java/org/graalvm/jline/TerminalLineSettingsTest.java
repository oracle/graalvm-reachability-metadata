/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.jline;

import jline.internal.Configuration;
import jline.internal.TerminalLineSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.ClassDescriptor;
import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.ClassOrdererContext;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class TerminalLineSettingsTest {

    private String originalConsoleProvider;
    private String originalShellCommand;
    private String originalSttyCommand;
    private Path temporaryDirectory;

    @BeforeEach
    void setUp() throws Exception {
        originalConsoleProvider = System.getProperty("jdk.console");
        originalShellCommand = System.getProperty("jline.sh");
        originalSttyCommand = System.getProperty("jline.stty");

        temporaryDirectory = Files.createTempDirectory("jline-terminal-line-settings-");
        Path fakeStty = writeFakeStty(temporaryDirectory.resolve("fake-stty"));

        System.setProperty("jdk.console", "jdk.internal.le");
        System.setProperty("jline.sh", temporaryDirectory.resolve("missing-sh").toString());
        System.setProperty("jline.stty", fakeStty.toString());
        Configuration.reset();
    }

    @AfterEach
    void tearDown() {
        restoreProperty("jdk.console", originalConsoleProvider);
        restoreProperty("jline.sh", originalShellCommand);
        restoreProperty("jline.stty", originalSttyCommand);
        Configuration.reset();
    }

    @Test
    void constructorUsesInheritedInputWhenAConsoleProviderIsAvailable() throws Exception {
        assumeTrue(!System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win"));

        assertThat(System.console()).isNotNull();

        TerminalLineSettings settings = new TerminalLineSettings();

        assertThat(settings.getTtyDevice()).isEqualTo(TerminalLineSettings.DEFAULT_TTY);
        assertThat(settings.getProperty("columns")).isEqualTo(80);
        assertThat(settings.getProperty("rows")).isEqualTo(24);
        assertThat(Files.readString(temporaryDirectory.resolve("stty-invocations.log")))
                .contains("-g")
                .contains("-a");
    }

    private Path writeFakeStty(final Path sttyScript) throws Exception {
        Path logFile = temporaryDirectory.resolve("stty-invocations.log");
        Files.writeString(sttyScript, "#!/bin/sh\n"
                + "printf '%s\\n' \"$*\" >> '" + escapeForSingleQuotedShell(logFile) + "'\n"
                + "case \"$1\" in\n"
                + "  -g)\n"
                + "    printf 'mock-terminal-state\\n'\n"
                + "    ;;\n"
                + "  -a)\n"
                + "    printf 'speed 9600 baud; 24 rows; 80 columns;\\nintr = ^C; lnext = ^V;\\n'\n"
                + "    ;;\n"
                + "  *)\n"
                + "    exit 0\n"
                + "    ;;\n"
                + "esac\n");
        assertThat(sttyScript.toFile().setExecutable(true)).isTrue();
        return sttyScript;
    }

    private static String escapeForSingleQuotedShell(final Path path) {
        return path.toString().replace("'", "'\"'\"'");
    }

    private static void restoreProperty(final String name, final String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }

    public static final class TerminalLineSettingsFirstOrderer implements ClassOrderer {

        @Override
        public void orderClasses(final ClassOrdererContext context) {
            context.getClassDescriptors().sort(Comparator
                    .comparingInt(TerminalLineSettingsFirstOrderer::priority)
                    .thenComparing(TerminalLineSettingsFirstOrderer::className));
        }

        private static int priority(final ClassDescriptor descriptor) {
            return className(descriptor).equals(TerminalLineSettingsTest.class.getName()) ? 0 : 1;
        }

        private static String className(final ClassDescriptor descriptor) {
            return descriptor.getTestClass().map(Class::getName).orElse(descriptor.getDisplayName());
        }
    }
}
