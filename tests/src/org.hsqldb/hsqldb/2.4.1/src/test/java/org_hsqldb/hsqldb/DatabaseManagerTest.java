/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.hsqldb.util.MainInvoker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class DatabaseManagerTest {
    @Test
    @Timeout(30)
    void mainPrintsUsageForHelpOption() throws Exception {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (PrintStream capture = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            System.setOut(capture);

            MainInvoker.invoke("org.hsqldb.util.DatabaseManager", new String[] { "--help" });
        } finally {
            System.setOut(originalOut);
        }

        assertThat(output.toString(StandardCharsets.UTF_8))
                .contains("Usage: java DatabaseManager [--options]")
                .contains("--driver <classname>")
                .contains("--noexit");
    }
}
