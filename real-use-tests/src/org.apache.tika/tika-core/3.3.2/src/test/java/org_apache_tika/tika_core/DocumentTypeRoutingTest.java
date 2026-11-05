/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tika.tika_core;

import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Tika's media type registry subsystem for routing uploaded documents.
public class DocumentTypeRoutingTest {
    @Test
    @Timeout(60)
    public void routesParameterizedXhtmlAsStructuredText() {
        MediaType suppliedType = MediaType.parse("application/xhtml+xml; charset=UTF-8");
        MediaTypeRegistry registry = MediaTypeRegistry.getDefaultRegistry();

        assertEquals(MediaType.APPLICATION_XML, registry.getSupertype(suppliedType.getBaseType()));
        assertEquals("UTF-8", suppliedType.getParameters().get("charset"));
        assertTrue(registry.isInstanceOf(suppliedType, MediaType.APPLICATION_XML));
        assertEquals("structured-text", routeFor(suppliedType, registry));
        assertEquals("binary", routeFor(MediaType.application("pdf"), registry));
    }

    private static String routeFor(MediaType contentType, MediaTypeRegistry registry) {
        if (registry.isInstanceOf(contentType, MediaType.APPLICATION_XML)) {
            return "structured-text";
        }
        if (registry.isInstanceOf(contentType, MediaType.TEXT_PLAIN)) {
            return "plain-text";
        }
        return "binary";
    }
}
