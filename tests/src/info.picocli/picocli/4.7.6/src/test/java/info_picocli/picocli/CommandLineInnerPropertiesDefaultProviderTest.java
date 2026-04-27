/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package info_picocli.picocli;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import static org.assertj.core.api.Assertions.assertThat;

public class CommandLineInnerPropertiesDefaultProviderTest {
    private static final String COMMAND_NAME = "defaults-from-classpath";
    private static final String DEFAULTS_PATH_PROPERTY = "picocli.defaults." + COMMAND_NAME + ".path";

    @Test
    void loadsDefaultsFromClasspathResourceWhenUserHomeFileIsAbsent(@TempDir Path userHome) {
        String previousUserHome = System.getProperty("user.home");
        String previousDefaultsPath = System.getProperty(DEFAULTS_PATH_PROPERTY);
        System.setProperty("user.home", userHome.toString());
        System.clearProperty(DEFAULTS_PATH_PROPERTY);
        try {
            ClasspathDefaultsCommand command = new ClasspathDefaultsCommand();

            new CommandLine(command).parseArgs();

            assertThat(command.message).isEqualTo("loaded from classpath");
        } finally {
            restoreProperty("user.home", previousUserHome);
            restoreProperty(DEFAULTS_PATH_PROPERTY, previousDefaultsPath);
        }
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }

    @Command(name = COMMAND_NAME, defaultValueProvider = CommandLine.PropertiesDefaultProvider.class)
    public static class ClasspathDefaultsCommand {
        @Option(names = "--message")
        String message;
    }
}
