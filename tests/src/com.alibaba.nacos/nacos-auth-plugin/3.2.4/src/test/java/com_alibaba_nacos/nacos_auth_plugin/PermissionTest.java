/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba_nacos.nacos_auth_plugin;

import com.alibaba.nacos.plugin.auth.api.Permission;
import com.alibaba.nacos.plugin.auth.api.Resource;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PermissionTest {
    @Test
    void bindsAnAuthorizationActionToItsResource() {
        Resource resource = new Resource("tenant-a", "application", "catalog", SignType.NAMING, new Properties());
        Permission permission = new Permission(resource, ActionTypes.READ.toString());

        assertSame(resource, permission.getResource());
        assertEquals("r", permission.getAction());
        assertTrue(permission.toString().contains("catalog"));
        assertTrue(permission.toString().contains("action='r'"));
    }

    @Test
    void allowsPermissionsToBeAssembledBeforeAuthorization() {
        Resource resource = new Resource("tenant-a", "application", "settings.yaml", SignType.CONFIG, new Properties());
        Permission permission = new Permission();
        permission.setResource(resource);
        permission.setAction(ActionTypes.WRITE.toString());

        assertSame(resource, permission.getResource());
        assertEquals("w", permission.getAction());
    }
}
