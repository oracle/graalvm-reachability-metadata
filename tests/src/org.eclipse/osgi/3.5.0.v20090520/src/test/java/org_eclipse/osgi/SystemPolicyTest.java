/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse.osgi;

import java.net.URL;
import java.util.Enumeration;
import java.util.Properties;

import org.eclipse.osgi.internal.loader.buddy.SystemPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SystemPolicyTest {
    @Test
    public void delegatesToExistingApplicationClassesAndResources() throws Exception {
        SystemPolicy policy = SystemPolicy.getInstance(SystemPolicy.APP);
        assertSame(String.class, policy.loadClass("java.lang.String"));
        String resource = "org_eclipse/osgi/catalog.properties";
        URL url = policy.loadResource(resource);
        assertNotNull(url);
        Properties catalog = new Properties();
        try (var input = url.openStream()) {
            catalog.load(input);
        }
        assertEquals("Catalog", catalog.getProperty("title"));
        Enumeration<?> resources = policy.loadResources(resource);
        assertNotNull(resources);
        assertTrue(resources.hasMoreElements());
        assertEquals(url, resources.nextElement());
    }
}
