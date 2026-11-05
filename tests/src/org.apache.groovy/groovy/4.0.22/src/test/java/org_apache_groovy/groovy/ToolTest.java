/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import groovyjarjarantlr4.v4.Tool;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ToolTest {
    @Test
    void configuresGrammarGenerationFromCommandLineOptions() {
        Tool tool = new Tool(new String[]{"-encoding", "UTF-8", "-visitor", "-no-listener", "-Werror"});
        assertEquals("UTF-8", tool.grammarEncoding);
        assertTrue(tool.gen_visitor);
        assertFalse(tool.gen_listener);
        assertTrue(tool.warnings_are_errors);
        assertEquals(0, tool.getNumErrors());
    }
}
