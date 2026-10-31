/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import groovyjarjarantlr4.v4.gui.TestRig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestRigTest {
    @TempDir
    Path directory;

    @Test
    void parsesSourceUsingThePrecompiledGroovyGrammar() throws Exception {
        Path source = directory.resolve("input.groovy");
        Files.writeString(source, "def total = 17");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream previous = System.out;
        try (PrintStream stream = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            System.setOut(stream);
            new TestRig(new String[]{"org.apache.groovy.parser.antlr4.Groovy", "compilationUnit", "-tree", source.toString()}).process();
        } finally {
            System.setOut(previous);
        }
        String tree = output.toString(StandardCharsets.UTF_8);
        assertTrue(tree.contains("(compilationUnit"));
        assertTrue(tree.contains("total"));
        assertTrue(tree.contains("17"));
    }
}
