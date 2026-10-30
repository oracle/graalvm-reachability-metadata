/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse.osgi;

import org.eclipse.osgi.framework.internal.core.FrameworkProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FrameworkPropertiesTest {
    @Test
    public void decodesUtf8EscapesWithoutChangingLiteralPlus() {
        assertEquals("file:/plugins/a b+c.jar", FrameworkProperties.decode("file:/plugins/a%20b+c.jar"));
        assertEquals("caf\u00e9", FrameworkProperties.decode("caf%C3%A9"));
        assertEquals("a+b", FrameworkProperties.decode("a%2Bb"));
    }
}
