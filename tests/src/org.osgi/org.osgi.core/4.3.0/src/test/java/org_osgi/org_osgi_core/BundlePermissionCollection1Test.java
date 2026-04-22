/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_osgi.org_osgi_core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.Permission;
import java.security.PermissionCollection;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.osgi.framework.BundlePermission;

public class BundlePermissionCollection1Test {
    @Test
    void permissionCollectionMergesActionsSerializesAndMatchesHierarchicalNames() throws Exception {
        BundlePermission wildcardPermission = new BundlePermission("*", "provide");
        BundlePermission packageFragmentPermission = new BundlePermission("com.example.*", "fragment");
        PermissionCollection permissionCollection = packageFragmentPermission.newPermissionCollection();
        permissionCollection.add(wildcardPermission);
        permissionCollection.add(packageFragmentPermission);
        permissionCollection.add(new BundlePermission("com.example.*", "require"));

        PermissionCollection restoredPermissionCollection = serializeAndDeserialize(permissionCollection);
        List<String> grantedPermissions = Collections.list(restoredPermissionCollection.elements()).stream()
                .map(Permission.class::cast)
                .map(permission -> (BundlePermission) permission)
                .map(permission -> permission.getName() + "=" + permission.getActions())
                .toList();

        assertThat(restoredPermissionCollection.implies(new BundlePermission("org.example.bundle", "require")))
                .isTrue();
        assertThat(restoredPermissionCollection.implies(new BundlePermission("com.example.bundle", "fragment")))
                .isTrue();
        assertThat(restoredPermissionCollection.implies(new BundlePermission("com.example.bundle", "host")))
                .isFalse();
        assertThat(grantedPermissions)
                .containsExactlyInAnyOrder("*=provide,require", "com.example.*=require,fragment");
        assertThatThrownBy(() -> restoredPermissionCollection.add(new RuntimePermission("exitVM")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid permission");
    }

    private static PermissionCollection serializeAndDeserialize(PermissionCollection permissionCollection)
            throws IOException, ClassNotFoundException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream)) {
            objectOutputStream.writeObject(permissionCollection);
        }
        try (ObjectInputStream objectInputStream =
                new ObjectInputStream(new ByteArrayInputStream(byteArrayOutputStream.toByteArray()))) {
            return (PermissionCollection) objectInputStream.readObject();
        }
    }
}
