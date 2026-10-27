/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.jline;

import org.jline.builtins.Source;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SourceInnerResourceSourceTest {

    @Test
    public void readsNamedBundledHelpResource() throws Exception {
        Source source = new Source.ResourceSource(
                "/org/jline/builtins/nano-main-help.txt", "nano help");

        assertEquals("nano help", source.getName());
        try (InputStream input = source.read()) {
            assertNotNull(input);
            String help = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(help.contains("Main nano help text"));
        }
    }
}
