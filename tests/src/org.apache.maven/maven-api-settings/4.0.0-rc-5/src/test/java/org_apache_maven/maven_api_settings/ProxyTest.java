/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_api_settings;

import org.apache.maven.api.settings.Proxy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProxyTest {
    @Test
    void suppliesProxyDefaultsAndConvertsStringSettings() {
        Proxy defaults = Proxy.newInstance();
        assertTrue(defaults.isActive());
        assertEquals("http", defaults.getProtocol());
        assertEquals(8080, defaults.getPort());

        Proxy proxy = Proxy.newBuilder().id("office")
                .activeString("true").protocol("https")
                .host("proxy.example.test").portString("3128")
                .username("developer").password("proxy-secret")
                .nonProxyHosts("localhost|*.example.test").build();

        assertEquals("office", proxy.getId());
        assertTrue(proxy.isActive());
        assertEquals("true", proxy.getActiveString());
        assertEquals("https", proxy.getProtocol());
        assertEquals("proxy.example.test", proxy.getHost());
        assertEquals("3128", proxy.getPortString());
        assertEquals(3128, proxy.getPort());
        assertEquals("developer", proxy.getUsername());
        assertEquals("proxy-secret", proxy.getPassword());
        assertEquals("localhost|*.example.test", proxy.getNonProxyHosts());

        Proxy inactive = proxy.withActiveString("false").withPortString("8443");
        assertFalse(inactive.isActive());
        assertEquals(8443, inactive.getPort());
        assertEquals(proxy.getHost(), inactive.getHost());
        assertEquals(proxy.getUsername(), inactive.getUsername());
        assertTrue(proxy.isActive());
        assertEquals(3128, proxy.getPort());
    }

    @Test
    void appliesSemanticDefaultsWhenRawValuesAreAbsent() {
        Proxy proxy = Proxy.newBuilder(false).host("proxy.example.test").build();

        assertTrue(proxy.isActive());
        assertEquals(8080, proxy.getPort());
    }
}
