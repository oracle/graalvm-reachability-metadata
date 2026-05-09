/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_janino.janino;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.codehaus.janino.SimpleCompiler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class SimpleCompilerTest {
    private static final String RESULT_PROPERTY = "janino.simple.compiler.main.result";

    @TempDir
    Path temporaryDirectory;

    @Test
    void commandLineEntryPointCompilesLoadsAndInvokesMainClass() throws Exception {
        final Path sourceFile = this.temporaryDirectory.resolve("JaninoMainEntry.java");
        Files.write(
                sourceFile,
                """
                public class JaninoMainEntry {
                    public static void main(String[] args) {
                        if (args.length != 2) {
                            throw new RuntimeException("Expected two arguments");
                        }
                        System.setProperty("janino.simple.compiler.main.result", args[0] + ":" + args[1]);
                    }
                }
                """.getBytes(StandardCharsets.UTF_8)
        );

        try {
            System.clearProperty(RESULT_PROPERTY);

            SimpleCompiler.main(new String[] {
                    sourceFile.toString(),
                    "JaninoMainEntry",
                    "left",
                    "right"
            });

            assertThat(System.getProperty(RESULT_PROPERTY)).isEqualTo("left:right");
        } catch (Throwable throwable) {
            JaninoNativeImageSupport.rethrowIfNotNativeImageDynamicClassLoadingFailure(throwable);
        } finally {
            System.clearProperty(RESULT_PROPERTY);
        }
    }
}
