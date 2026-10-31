/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.hsqldb.util.DatabaseManagerSwing;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class DatabaseManagerSwingTest {
    @Test
    void printsCommandLineHelpWithoutOpeningAWindow() {
        PrintStream original = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        String oldDrawing = System.getProperty("sun.java2d.noddraw");
        try (PrintStream capture = new PrintStream(buffer, true, StandardCharsets.UTF_8)) {
            System.setOut(capture);
            DatabaseManagerSwing.main(new String[]{"--noexit", "--help"});
            String help = buffer.toString(StandardCharsets.UTF_8);
            assertTrue(help.contains("--url"));
            assertTrue(help.contains("--driver"));
        } finally {
            System.setOut(original);
            if (oldDrawing == null) {
                System.clearProperty("sun.java2d.noddraw");
            } else {
                System.setProperty("sun.java2d.noddraw", oldDrawing);
            }
        }
    }
}
