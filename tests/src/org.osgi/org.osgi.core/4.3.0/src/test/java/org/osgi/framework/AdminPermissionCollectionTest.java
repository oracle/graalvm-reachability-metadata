/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.osgi.framework;

import java.security.Permission;
import java.security.PermissionCollection;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AdminPermissionCollectionTest {
    @Test
    void createsCollectionAndReturnsAddedPermissions() {
        AdminPermission grantedPermission = new AdminPermission("*", "class");
        PermissionCollection permissionCollection = new AdminPermissionCollection();

        permissionCollection.add(grantedPermission);
        List<Permission> permissions = Collections.list(permissionCollection.elements());

        assertThat(permissions).containsExactly(grantedPermission);
    }

    @Test
    void resolvesHashtableClassViaCompilerGeneratedHelper() {
        Class<?> resolvedClass = AdminPermissionCollection.class$("java.util.Hashtable");

        assertThat(resolvedClass).isEqualTo(Hashtable.class);
    }

    @Test
    void rejectsRequestedPermissionsCreatedFromFilterExpressions() {
        PermissionCollection permissionCollection = new AdminPermission("*", "class")
                .newPermissionCollection();
        permissionCollection.add(new AdminPermission("*", "class"));

        boolean implied = permissionCollection.implies(
                new AdminPermission("(name=org.example.bundle)", "class"));

        assertThat(implied).isFalse();
    }
}
