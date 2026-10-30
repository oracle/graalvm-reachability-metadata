/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_api_toolchain;

import org.apache.maven.api.toolchain.InputLocation;
import org.apache.maven.api.toolchain.InputSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class InputSourceTest {
    @Test
    void exposesDescriptorLocationForDiagnostics() {
        InputSource source = new InputSource("toolchains.xml");
        InputLocation location = new InputLocation(6, 3, source);

        assertEquals("toolchains.xml", source.getLocation());
        assertEquals("toolchains.xml", source.toString());
        assertSame(source, location.getSource());
        assertEquals("toolchains.xml", location.getSource().getLocation());
    }

    @Test
    void permitsUnknownDescriptorLocation() {
        InputSource source = new InputSource(null);

        assertNull(source.getLocation());
        assertNull(new InputLocation(source).getSource().getLocation());
    }
}
