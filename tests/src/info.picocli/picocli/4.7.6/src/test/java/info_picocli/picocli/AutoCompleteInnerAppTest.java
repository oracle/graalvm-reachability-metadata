/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package info_picocli.picocli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import picocli.AutoComplete;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import static org.assertj.core.api.Assertions.assertThat;

public class AutoCompleteInnerAppTest {

    private static final String EXIT_ON_SUCCESS_PROPERTY = "picocli.autocomplete.systemExitOnSuccess";
    private static final String EXIT_ON_ERROR_PROPERTY = "picocli.autocomplete.systemExitOnError";

    @TempDir
    Path tempDir;

    @Test
    void generatesCompletionScriptFromNamedCommandAndFactoryClasses() throws IOException {
        Path completionScript = tempDir.resolve("sample_completion");
        String previousExitOnSuccess = System.getProperty(EXIT_ON_SUCCESS_PROPERTY);
        String previousExitOnError = System.getProperty(EXIT_ON_ERROR_PROPERTY);
        TrackingFactory.createdCommand = false;

        System.setProperty(EXIT_ON_SUCCESS_PROPERTY, "false");
        System.setProperty(EXIT_ON_ERROR_PROPERTY, "false");
        try {
            AutoComplete.main(
                    "--factory", TrackingFactory.class.getName(),
                    "--completionScript", completionScript.toString(),
                    "--name", "sample",
                    "--force",
                    SampleCommand.class.getName());
        } finally {
            restoreProperty(EXIT_ON_SUCCESS_PROPERTY, previousExitOnSuccess);
            restoreProperty(EXIT_ON_ERROR_PROPERTY, previousExitOnError);
        }

        assertThat(TrackingFactory.createdCommand).isTrue();
        assertThat(completionScript).exists().isRegularFile();
        assertThat(Files.readString(completionScript, StandardCharsets.UTF_8))
                .contains("sample", "--flag");
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }

    @Command(name = "sample")
    public static class SampleCommand implements Runnable {
        @Option(names = "--flag", description = "Flag included in the generated completion script.")
        boolean flag;

        @Override
        public void run() {
        }
    }

    public static class TrackingFactory implements CommandLine.IFactory {
        static boolean createdCommand;

        @Override
        public <K> K create(Class<K> cls) {
            if (cls == SampleCommand.class) {
                createdCommand = true;
                return cls.cast(new SampleCommand());
            }
            throw new IllegalArgumentException("Unexpected factory request: " + cls.getName());
        }
    }
}
