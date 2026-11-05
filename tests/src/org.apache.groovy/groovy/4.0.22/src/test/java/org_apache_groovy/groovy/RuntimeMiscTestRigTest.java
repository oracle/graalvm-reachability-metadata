/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import groovyjarjarantlr4.v4.runtime.misc.TestRig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class RuntimeMiscTestRigTest {
    @TempDir
    Path directory;

    @Test
    void delegatesTokenizationToTheGrammarTool() throws Exception {
        Path source = directory.resolve("input.groovy");
        Files.writeString(source, "def reference = 'order'");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream previous = System.out;
        try (PrintStream stream = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            System.setOut(stream);
            TestRig.main(new String[]{"org.apache.groovy.parser.antlr4.GroovyLexer", "tokens", "-tokens", source.toString()});
        } finally {
            System.setOut(previous);
        }
        String tokens = output.toString(StandardCharsets.UTF_8);
        assertTrue(tokens.contains("reference"));
        assertTrue(tokens.contains("order"));
        assertTrue(tokens.contains("EOF"));
    }
}
