/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse.osgi;

import java.util.PropertyPermission;

import org.eclipse.osgi.internal.permadmin.PermissionInfoCollection;
import org.junit.jupiter.api.Test;
import org.osgi.service.permissionadmin.PermissionInfo;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PermissionInfoCollectionTest {
    @Test
    public void constructsAndCachesPermissionsFromDescriptors() {
        PermissionInfo[] infos = {
                new PermissionInfo(PropertyPermission.class.getName(), "application.*", "read"),
                new PermissionInfo(RuntimePermission.class.getName(), "getClassLoader", null)
        };
        PermissionInfoCollection permissions = new PermissionInfoCollection(infos);
        assertTrue(permissions.implies(new PropertyPermission("application.theme", "read")));
        assertTrue(permissions.implies(new PropertyPermission("application.locale", "read")));
        assertFalse(permissions.implies(new PropertyPermission("application.theme", "write")));
        assertFalse(permissions.implies(new PropertyPermission("unrelated.theme", "read")));
        assertTrue(permissions.implies(new RuntimePermission("getClassLoader")));
        assertFalse(permissions.implies(new RuntimePermission("setContextClassLoader")));
    }
}
