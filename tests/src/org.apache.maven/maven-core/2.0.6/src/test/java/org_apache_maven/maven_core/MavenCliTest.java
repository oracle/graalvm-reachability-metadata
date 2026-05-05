/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_core;

import org.apache.maven.cli.MavenCli;
import org.codehaus.classworlds.ClassWorld;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MavenCliTest {
    @Test
    public void mainWithVersionOptionPrintsBundledMavenVersion() throws Exception {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));

            int exitCode = MavenCli.main(new String[] {"--version"}, new ClassWorld());

            assertEquals(0, exitCode);
        } finally {
            System.setOut(originalOut);
        }

        String versionOutput = output.toString(StandardCharsets.UTF_8);
        assertTrue(versionOutput.contains("Maven version:"), versionOutput);
        assertTrue(!versionOutput.contains("unknown"), versionOutput);
    }
}
