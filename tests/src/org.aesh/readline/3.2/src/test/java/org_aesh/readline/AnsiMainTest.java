/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aesh.readline;

import org.jline.jansi.AnsiMain;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class AnsiMainTest {

    @Test
    void readsBundledPomPropertiesVersion() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(AnsiMain.class, MethodHandles.lookup());
        MethodHandle getPomPropertiesVersion = lookup.findStatic(
                AnsiMain.class,
                "getPomPropertiesVersion",
                MethodType.methodType(String.class, String.class));

        String version = (String) getPomPropertiesVersion.invokeExact("org.aesh/readline");

        assertThat(version).isNotBlank();
    }

    @Test
    void mainPrintsDiagnosticsAndRunsBundledLogoDemo() throws Exception {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        ByteArrayOutputStream capturedOutput = new ByteArrayOutputStream();
        ByteArrayOutputStream capturedError = new ByteArrayOutputStream();

        try (PrintStream output = new PrintStream(capturedOutput, true, StandardCharsets.UTF_8);
                PrintStream error = new PrintStream(capturedError, true, StandardCharsets.UTF_8)) {
            System.setOut(output);
            System.setErr(error);

            AnsiMain.main();
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }

        String output = capturedOutput.toString(StandardCharsets.UTF_8);

        assertThat(output)
                .contains("System properties")
                .contains("OSUtils")
                .contains("Jansi")
                .contains("IS_WINDOWS");
    }
}
