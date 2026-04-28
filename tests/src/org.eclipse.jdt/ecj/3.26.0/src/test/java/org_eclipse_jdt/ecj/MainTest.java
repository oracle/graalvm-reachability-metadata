/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jdt.ecj;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jdt.core.compiler.batch.BatchCompiler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class MainTest {
    private static final String MODULE_VERSION = "1.2.3";

    @TempDir
    Path temporaryDirectory;

    @Test
    void batchCompilerAcceptsValidModuleVersionOption() throws IOException {
        Path outputDirectory = this.temporaryDirectory.resolve("classes");
        Files.createDirectories(outputDirectory);
        Path sourceFile = writeSourceFile();
        StringWriter compilerOutput = new StringWriter();
        StringWriter compilerErrors = new StringWriter();

        boolean compiled = BatchCompiler.compile(new String[] {
                "-proc:none",
                "--module-version", MODULE_VERSION,
                "-d", outputDirectory.toString(),
                sourceFile.toString()
        }, new PrintWriter(compilerOutput), new PrintWriter(compilerErrors), null);

        assertThat(compiled)
                .describedAs(formatCompilerMessages(compilerOutput, compilerErrors))
                .isTrue();
        assertThat(outputDirectory.resolve(Path.of("org_eclipse_jdt", "ecj", "fixture", "ModuleVersionFixture.class")))
                .exists();
    }

    private Path writeSourceFile() throws IOException {
        Path packageDirectory = this.temporaryDirectory.resolve(Path.of("src", "org_eclipse_jdt", "ecj", "fixture"));
        Files.createDirectories(packageDirectory);
        Path sourceFile = packageDirectory.resolve("ModuleVersionFixture.java");
        Files.writeString(sourceFile, """
                package org_eclipse_jdt.ecj.fixture;

                public class ModuleVersionFixture {
                    public String message() {
                        return "compiled";
                    }
                }
                """, StandardCharsets.UTF_8);
        return sourceFile;
    }

    private static String formatCompilerMessages(StringWriter compilerOutput, StringWriter compilerErrors) {
        return "compiler output:" + System.lineSeparator() + compilerOutput
                + System.lineSeparator() + "compiler errors:" + System.lineSeparator() + compilerErrors;
    }
}
