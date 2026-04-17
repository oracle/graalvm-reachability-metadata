/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.osgi.framework;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.Permission;
import java.security.PermissionCollection;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Assumptions;
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
    void serializesAndDeserializesCollectionWithGrantedPermission() throws IOException, ClassNotFoundException {
        Assumptions.assumeFalse(isNativeImageRuntime());

        AdminPermission grantedPermission = new AdminPermission("*", "class");
        PermissionCollection permissionCollection = grantedPermission.newPermissionCollection();
        permissionCollection.add(grantedPermission);

        PermissionCollection restoredPermissionCollection = serializeAndDeserialize(permissionCollection);

        assertThat(Collections.list(restoredPermissionCollection.elements()))
                .singleElement()
                .isEqualTo(grantedPermission);
    }

    @Test
    void mergesActionsForDuplicateNamesAndImpliesRequestedPermission() {
        PermissionCollection permissionCollection = new AdminPermission("*", "class")
                .newPermissionCollection();
        permissionCollection.add(new AdminPermission("*", "class"));
        permissionCollection.add(new AdminPermission("*", "execute"));

        boolean implied = permissionCollection.implies(new AdminPermission("*", "execute"));

        List<Permission> permissions = Collections.list(permissionCollection.elements());

        assertThat(implied).isTrue();
        assertThat(permissions)
                .singleElement()
                .isInstanceOfSatisfying(AdminPermission.class, permission ->
                        assertThat(permission.getActions()).isEqualTo("class,execute,resolve"));
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

    private PermissionCollection serializeAndDeserialize(PermissionCollection permissionCollection)
            throws IOException, ClassNotFoundException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream)) {
            objectOutputStream.writeObject(permissionCollection);
        }

        try (ObjectInputStream objectInputStream = new ObjectInputStream(
                new ByteArrayInputStream(byteArrayOutputStream.toByteArray()))) {
            return (PermissionCollection) objectInputStream.readObject();
        }
    }

    private boolean isNativeImageRuntime() {
        return "runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"));
    }
}
