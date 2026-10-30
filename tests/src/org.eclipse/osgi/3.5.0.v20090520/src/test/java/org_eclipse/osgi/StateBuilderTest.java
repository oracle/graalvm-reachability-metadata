/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse.osgi;

import java.net.URI;
import java.util.Map;

import org.eclipse.osgi.service.resolver.BundleDescription;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class StateBuilderTest {
    @Test
    public void parsesTypedExportAttributesThroughTheStateFactory() throws Exception {
        BundleDescription bundle = ResolverFixtures.state().getBundle(17L);
        Map<?, ?> attributes = bundle.getExportPackages()[0].getAttributes();
        assertEquals("example.catalog", bundle.getSymbolicName());
        assertEquals("example.catalog.api", bundle.getExportPackages()[0].getName());
        assertEquals(URI.create("https://example.invalid/catalog"), attributes.get("endpoint"));
        assertEquals(7L, attributes.get("priority"));
    }
}
