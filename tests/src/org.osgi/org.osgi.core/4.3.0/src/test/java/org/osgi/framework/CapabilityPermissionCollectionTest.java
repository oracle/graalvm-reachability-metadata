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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CapabilityPermissionCollectionTest {
    @Test
    void createsCollectionAndReturnsAddedPermissions() {
        CapabilityPermission grantedPermission = new CapabilityPermission(
                "org.example.capability", CapabilityPermission.PROVIDE);
        PermissionCollection permissionCollection = new CapabilityPermissionCollection();

        permissionCollection.add(grantedPermission);
        List<Permission> permissions = Collections.list(permissionCollection.elements());

        assertThat(permissions).containsExactly(grantedPermission);
    }

    @Test
    void mergesActionsForDuplicateNamesAndImpliesRequestedPermissions() {
        PermissionCollection permissionCollection = new CapabilityPermissionCollection();
        permissionCollection.add(new CapabilityPermission(
                "org.example.capability", CapabilityPermission.PROVIDE));
        permissionCollection.add(new CapabilityPermission(
                "org.example.capability", CapabilityPermission.REQUIRE));

        boolean providesCapability = permissionCollection.implies(new CapabilityPermission(
                "org.example.capability", CapabilityPermission.PROVIDE));
        boolean requiresCapability = permissionCollection.implies(new CapabilityPermission(
                "org.example.capability", CapabilityPermission.REQUIRE));

        List<Permission> permissions = Collections.list(permissionCollection.elements());

        assertThat(providesCapability).isTrue();
        assertThat(requiresCapability).isTrue();
        assertThat(permissions)
                .singleElement()
                .isInstanceOfSatisfying(CapabilityPermission.class, permission ->
                        assertThat(permission.getActions()).isEqualTo("require,provide"));
    }

    @Test
    void initializesCapabilityPermissionCollectionClass() throws ClassNotFoundException {
        assertThat(Class.forName("org.osgi.framework.CapabilityPermissionCollection")).isNotNull();
    }

    @Test
    void serializesAndDeserializesCollectionWithGrantedPermission() throws IOException, ClassNotFoundException {
        CapabilityPermission grantedPermission = new CapabilityPermission(
                "org.example.capability", CapabilityPermission.PROVIDE);
        PermissionCollection permissionCollection = new CapabilityPermissionCollection();
        permissionCollection.add(grantedPermission);

        PermissionCollection restoredPermissionCollection = serializeAndDeserialize(permissionCollection);

        assertThat(Collections.list(restoredPermissionCollection.elements()))
                .singleElement()
                .isEqualTo(grantedPermission);
        assertThat(restoredPermissionCollection.implies(new CapabilityPermission(
                "org.example.capability", CapabilityPermission.PROVIDE))).isTrue();
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
}
