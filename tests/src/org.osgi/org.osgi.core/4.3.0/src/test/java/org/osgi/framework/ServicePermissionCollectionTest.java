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

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.assertj.core.api.Assertions.assertThat;

@TestMethodOrder(MethodOrderer.MethodName.class)
public class ServicePermissionCollectionTest {
    @Test
    void a_createsCollectionAndReturnsAddedPermissions() {
        ServicePermission grantedPermission = new ServicePermission(
                "org.example.service.ExampleService", ServicePermission.GET);
        PermissionCollection permissionCollection = grantedPermission.newPermissionCollection();

        permissionCollection.add(grantedPermission);
        List<Permission> permissions = Collections.list(permissionCollection.elements());

        assertThat(permissions).containsExactly(grantedPermission);
    }

    @Test
    void mergesActionsForDuplicateNamesAndImpliesRequestedPermissions() {
        PermissionCollection permissionCollection = new ServicePermission(
                "org.example.service.ExampleService", ServicePermission.GET).newPermissionCollection();
        permissionCollection.add(new ServicePermission(
                "org.example.service.ExampleService", ServicePermission.GET));
        permissionCollection.add(new ServicePermission(
                "org.example.service.ExampleService", ServicePermission.REGISTER));

        boolean getsService = permissionCollection.implies(new ServicePermission(
                "org.example.service.ExampleService", ServicePermission.GET));
        boolean registersService = permissionCollection.implies(new ServicePermission(
                "org.example.service.ExampleService", ServicePermission.REGISTER));
        List<Permission> permissions = Collections.list(permissionCollection.elements());

        assertThat(getsService).isTrue();
        assertThat(registersService).isTrue();
        assertThat(permissions)
                .singleElement()
                .isInstanceOfSatisfying(ServicePermission.class, permission ->
                        assertThat(permission.getActions()).isEqualTo("get,register"));
    }

    @Test
    void keepsFilterPermissionsInCollectionElementsAndImpliesMatchingRequests() {
        ServicePermission filterPermission = new ServicePermission(
                "(" + Constants.OBJECTCLASS + "=org.example.service.ExampleService)",
                ServicePermission.GET);
        PermissionCollection permissionCollection = new ServicePermission(
                "org.example.service.ExampleService", ServicePermission.GET).newPermissionCollection();

        permissionCollection.add(filterPermission);
        List<Permission> permissions = Collections.list(permissionCollection.elements());
        boolean implied = permissionCollection.implies(new ServicePermission(
                "org.example.service.ExampleService", ServicePermission.GET));

        assertThat(permissions).containsExactly(filterPermission);
        assertThat(implied).isTrue();
    }

    @Test
    void serializesAndDeserializesCollectionWithGrantedAndFilterPermissions()
            throws IOException, ClassNotFoundException {
        ServicePermission grantedPermission = new ServicePermission(
                "org.example.service.ExampleService", ServicePermission.REGISTER);
        ServicePermission filterPermission = new ServicePermission(
                "(" + Constants.OBJECTCLASS + "=org.example.service.ExampleService)",
                ServicePermission.GET);
        PermissionCollection permissionCollection = grantedPermission.newPermissionCollection();
        permissionCollection.add(grantedPermission);
        permissionCollection.add(filterPermission);

        PermissionCollection restoredPermissionCollection = serializeAndDeserialize(permissionCollection);

        assertThat(Collections.list(restoredPermissionCollection.elements()))
                .containsExactlyInAnyOrder(grantedPermission, filterPermission);
        assertThat(restoredPermissionCollection.implies(new ServicePermission(
                "org.example.service.ExampleService", ServicePermission.GET))).isTrue();
        assertThat(restoredPermissionCollection.implies(new ServicePermission(
                "org.example.service.ExampleService", ServicePermission.REGISTER))).isTrue();
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
