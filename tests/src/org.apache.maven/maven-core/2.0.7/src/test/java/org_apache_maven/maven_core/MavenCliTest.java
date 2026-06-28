/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_core;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.apache.maven.cli.MavenCli;
import org.codehaus.classworlds.ClassWorld;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MavenCliTest {
    @Test
    void showsBundledVersionInformation() {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        try (PrintStream capturedOut = new PrintStream(out, true, StandardCharsets.UTF_8);
                PrintStream capturedErr = new PrintStream(err, true, StandardCharsets.UTF_8)) {
            System.setOut(capturedOut);
            System.setErr(capturedErr);

            int exitCode = MavenCli.main(new String[] {"--version"}, new ClassWorld());

            assertThat(exitCode).isZero();
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }

        String output = out.toString(StandardCharsets.UTF_8);
        String errorOutput = err.toString(StandardCharsets.UTF_8);

        assertThat(output).startsWith("Maven version: ");
        assertThat(output).doesNotContain("unknown");
        assertThat(errorOutput).isEmpty();
    }
}
