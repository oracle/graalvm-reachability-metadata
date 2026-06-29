/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_janino.janino;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.codehaus.janino.SimpleCompiler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class SimpleCompilerTest {
    @Test
    void mainCompilesLoadsAndInvokesGeneratedMainClass(@TempDir Path tempDir) throws Exception {
        final Path sourceFile = tempDir.resolve("GeneratedMain.java");
        Files.writeString(sourceFile, """
                public class GeneratedMain {
                    public static void main(String[] args) {
                        System.out.println("janino:" + args[0] + ":" + args[1]);
                    }
                }
                """, StandardCharsets.UTF_8);

        try {
            final PrintStream originalOut = System.out;
            final ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (PrintStream replacementOut = new PrintStream(output, true, StandardCharsets.UTF_8)) {
                System.setOut(replacementOut);
                SimpleCompiler.main(new String[] {sourceFile.toString(), "GeneratedMain", "left", "right" });
            } finally {
                System.setOut(originalOut);
            }

            assertThat(output.toString(StandardCharsets.UTF_8)).contains("janino:left:right");
        } catch (Throwable throwable) {
            NativeImageDynamicClassLoadingSupport.rethrowIfNotNativeImageDynamicClassLoadingFailure(throwable);
        }
    }
}
