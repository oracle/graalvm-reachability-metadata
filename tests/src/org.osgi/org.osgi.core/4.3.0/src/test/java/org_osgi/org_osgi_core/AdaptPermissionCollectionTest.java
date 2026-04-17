/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_osgi.org_osgi_core;

import java.security.Permission;
import java.security.PermissionCollection;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.osgi.framework.AdaptPermission;

import static org.assertj.core.api.Assertions.assertThat;

public class AdaptPermissionCollectionTest {
    @Test
    void createsCollectionAndReturnsAddedPermissions() {
        AdaptPermission grantedPermission = new AdaptPermission("*", AdaptPermission.ADAPT);
        PermissionCollection permissionCollection = grantedPermission.newPermissionCollection();

        permissionCollection.add(grantedPermission);
        List<Permission> permissions = Collections.list(permissionCollection.elements());

        assertThat(permissions).containsExactly(grantedPermission);
    }

    @Test
    void rejectsRequestedPermissionsCreatedFromFilterExpressions() {
        PermissionCollection permissionCollection = new AdaptPermission("*", AdaptPermission.ADAPT)
                .newPermissionCollection();
        permissionCollection.add(new AdaptPermission("*", AdaptPermission.ADAPT));

        boolean implied = permissionCollection.implies(
                new AdaptPermission("(adaptClass=java.lang.String)", AdaptPermission.ADAPT));

        assertThat(implied).isFalse();
    }
}
