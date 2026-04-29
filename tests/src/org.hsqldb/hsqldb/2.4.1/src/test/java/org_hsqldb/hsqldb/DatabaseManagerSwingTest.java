/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.hsqldb.util.DatabaseManagerSwing;
import org.junit.jupiter.api.Test;

public class DatabaseManagerSwingTest {
    @Test
    public void printsUsageForHelpOption() throws Exception {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (PrintStream printStream = new PrintStream(output, true, StandardCharsets.UTF_8.name())) {
            System.setOut(printStream);

            DatabaseManagerSwing.main(new String[]{"--help"});
        } finally {
            System.setOut(originalOut);
        }

        String usage = output.toString(StandardCharsets.UTF_8.name());

        assertTrue(usage.contains("Usage: java DatabaseManagerSwing"));
        assertTrue(usage.contains("--driver <classname>"));
        assertTrue(usage.contains("--noexit"));
    }
}
