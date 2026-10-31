/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_core;

import java.util.List;

import org.apache.logging.log4j.core.tools.picocli.CommandLine.Help.Ansi;
import org.apache.logging.log4j.core.tools.picocli.CommandLine.Help.Ansi.Style;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ResourceLock(Resources.SYSTEM_PROPERTIES)
public class CommandLineInnerHelpInnerAnsiTest {
    @Test
    void appliesStylesAndHonorsExplicitAnsiPreferences() {
        String styled = Ansi.ON.apply("ready", List.of(Style.bold)).toString();
        assertTrue(styled.contains("\u001b[1m"));
        assertTrue(styled.contains("ready"));
        assertEquals("ready", Ansi.OFF.apply("ready", List.of(Style.bold)).toString());
        assertTrue(TestSupport.withProperty("picocli.ansi", "true", Ansi.AUTO::enabled));
        assertFalse(TestSupport.withProperty("picocli.ansi", "false", Ansi.AUTO::enabled));
    }
}
