/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse.osgi;

import org.eclipse.osgi.internal.permadmin.SecurityRow;
import org.junit.jupiter.api.Test;
import org.osgi.service.condpermadmin.ConditionInfo;
import org.osgi.service.condpermadmin.ConditionalPermissionInfo;
import org.osgi.service.permissionadmin.PermissionInfo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

public class SecurityRowTest {
    @Test
    public void returnsDefensiveCopiesOfPolicyDescriptors() {
        ConditionInfo condition = new ConditionInfo("org.osgi.service.condpermadmin.BundleLocationCondition", new String[]{"file:*"});
        PermissionInfo permission = new PermissionInfo("java.util.PropertyPermission", "application.*", "read");
        ConditionalPermissionInfo row = new SecurityRow(null, "application-policy", new ConditionInfo[]{condition},
                new PermissionInfo[]{permission}, ConditionalPermissionInfo.ALLOW);

        ConditionInfo[] conditions = row.getConditionInfos();
        PermissionInfo[] permissions = row.getPermissionInfos();
        assertNotSame(conditions, row.getConditionInfos());
        assertNotSame(permissions, row.getPermissionInfos());
        conditions[0] = null;
        permissions[0] = null;
        assertEquals(condition, row.getConditionInfos()[0]);
        assertEquals(permission, row.getPermissionInfos()[0]);
        assertEquals("application-policy", row.getName());
        assertEquals(ConditionalPermissionInfo.ALLOW, row.getAccessDecision());
    }
}
