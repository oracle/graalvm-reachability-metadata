/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba_nacos.nacos_auth_plugin;

import com.alibaba.nacos.plugin.auth.api.Resource;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ResourceTest {
    @Test
    void describesTheTenantScopedResourceAndItsAuthorizationAttributes() {
        Properties attributes = new Properties();
        attributes.setProperty("owner", "configuration-team");
        Resource resource = new Resource("tenant-a", "application", "settings.yaml", SignType.CONFIG, attributes);

        assertEquals("tenant-a", resource.getNamespaceId());
        assertEquals("application", resource.getGroup());
        assertEquals("settings.yaml", resource.getName());
        assertEquals(SignType.CONFIG, resource.getType());
        assertSame(attributes, resource.getProperties());
        assertEquals("configuration-team", resource.getProperties().getProperty("owner"));
        assertTrue(resource.toString().contains("settings.yaml"));
        assertTrue(resource.toString().contains("owner=configuration-team"));
    }

    @Test
    void emptyResourceRepresentsAnUnscopedOperation() {
        Resource resource = Resource.EMPTY_RESOURCE;

        assertEquals("", resource.getNamespaceId());
        assertEquals("", resource.getGroup());
        assertEquals("", resource.getName());
        assertEquals("", resource.getType());
        assertNull(resource.getProperties());
    }
}
