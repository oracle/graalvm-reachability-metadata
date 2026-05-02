/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_extras_beanshell.bsh;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import bsh.Interpreter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

public class InterpreterTest {
    @Test
    void invokesResolvedStaticMainMethod(@TempDir Path temporaryDirectory) throws Exception {
        Path script = temporaryDirectory.resolve("invoke-main.bsh");
        Files.writeString(script, "print(\"invoked through BeanShell main\");\n", UTF_8);

        PrintStream originalOut = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PrintStream capturedOut = new PrintStream(output, true, UTF_8)) {
            System.setOut(capturedOut);

            Interpreter.invokeMain(Interpreter.class, new String[] {script.toString()});
        } finally {
            System.setOut(originalOut);
        }

        assertThat(output.toString(UTF_8)).contains("invoked through BeanShell main");
    }
}
