/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_osgi.org_osgi_core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.Permission;
import java.security.PermissionCollection;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.osgi.framework.BundlePermission;

public class BundlePermissionCollection1Test {
    @Test
    void permissionCollectionMergesActionsAndMatchesWildcardAndHierarchicalNames() {
        BundlePermission wildcardPermission = new BundlePermission("*", "provide");
        BundlePermission packageFragmentPermission = new BundlePermission("com.example.*", "fragment");
        PermissionCollection permissionCollection = packageFragmentPermission.newPermissionCollection();
        permissionCollection.add(wildcardPermission);
        permissionCollection.add(packageFragmentPermission);
        permissionCollection.add(new BundlePermission("com.example.*", "require"));

        List<String> grantedPermissions = Collections.list(permissionCollection.elements()).stream()
                .map(Permission.class::cast)
                .map(permission -> (BundlePermission) permission)
                .map(permission -> permission.getName() + "=" + permission.getActions())
                .toList();

        assertThat(permissionCollection.implies(new BundlePermission("org.example.bundle", "require"))).isTrue();
        assertThat(permissionCollection.implies(new BundlePermission("com.example.bundle", "fragment"))).isTrue();
        assertThat(permissionCollection.implies(new BundlePermission("com.example.bundle", "host"))).isFalse();
        assertThat(grantedPermissions)
                .containsExactlyInAnyOrder("*=provide,require", "com.example.*=require,fragment");
        assertThatThrownBy(() -> permissionCollection.add(new RuntimePermission("exitVM")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid permission");
    }
}
