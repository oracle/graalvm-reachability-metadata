/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_tunnelvisionlabs.antlr4;

import org.antlr.v4.Tool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(120)
public class ToolTest {
    @TempDir
    Path directory;

    @Test
    void configuresGenerationFromCommandLineOptions() {
        Tool tool = new Tool(new String[]{
                "-o", directory.toString(), "-lib", directory.toString(),
                "-encoding", "UTF-8", "-package", "example.generated",
                "-no-listener", "-visitor", "-atn", "-Werror"
        });

        assertEquals(0, tool.getNumErrors());
        assertEquals(directory.toString(), tool.outputDirectory);
        assertEquals(directory.toString(), tool.libDirectory);
        assertEquals("UTF-8", tool.grammarEncoding);
        assertEquals("example.generated", tool.genPackage);
        assertFalse(tool.gen_listener);
        assertTrue(tool.gen_visitor);
        assertTrue(tool.generate_ATN_dot);
        assertTrue(tool.warnings_are_errors);
    }

    @Test
    void appliesBooleanOptionsInArgumentOrder() {
        Tool tool = new Tool(new String[]{
                "-no-listener", "-listener", "-visitor", "-no-visitor"
        });

        assertEquals(0, tool.getNumErrors());
        assertTrue(tool.gen_listener);
        assertFalse(tool.gen_visitor);
    }
}
