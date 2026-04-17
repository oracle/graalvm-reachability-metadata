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

public class PackagePermissionCollectionTest {
    @Test
    void createsCollectionAndReturnsAddedPermissions() {
        PackagePermission grantedPermission = new PackagePermission("org.example.api", PackagePermission.IMPORT);
        PermissionCollection permissionCollection = new PackagePermissionCollection();

        permissionCollection.add(grantedPermission);
        List<Permission> permissions = Collections.list(permissionCollection.elements());

        assertThat(permissions).containsExactly(grantedPermission);
    }

    @Test
    void mergesActionsForDuplicateNamesAndImpliesRequestedPermissions() {
        PermissionCollection permissionCollection = new PackagePermissionCollection();
        permissionCollection.add(new PackagePermission("org.example.api", PackagePermission.EXPORTONLY));
        permissionCollection.add(new PackagePermission("org.example.api", PackagePermission.IMPORT));

        boolean exportsPackage = permissionCollection.implies(
                new PackagePermission("org.example.api", PackagePermission.EXPORTONLY));
        boolean importsPackage = permissionCollection.implies(
                new PackagePermission("org.example.api", PackagePermission.IMPORT));

        List<Permission> permissions = Collections.list(permissionCollection.elements());

        assertThat(exportsPackage).isTrue();
        assertThat(importsPackage).isTrue();
        assertThat(permissions)
                .singleElement()
                .isInstanceOfSatisfying(PackagePermission.class, permission ->
                        assertThat(permission.getActions()).isEqualTo("exportonly,import"));
    }

    @Test
    void keepsFilterPermissionsInCollectionElementsAndImpliesMatchingImports() {
        PackagePermission filterPermission = new PackagePermission(
                "(package.name=org.example.api)", PackagePermission.IMPORT);
        PermissionCollection permissionCollection = new PackagePermissionCollection();

        permissionCollection.add(filterPermission);
        List<Permission> permissions = Collections.list(permissionCollection.elements());
        boolean implied = permissionCollection.implies(
                new PackagePermission("org.example.api", PackagePermission.IMPORT));

        assertThat(permissions).containsExactly(filterPermission);
        assertThat(implied).isTrue();
    }

    @Test
    void serializesAndDeserializesCollectionWithGrantedAndFilterPermissions()
            throws IOException, ClassNotFoundException {
        PackagePermission grantedPermission = new PackagePermission("org.example.api", PackagePermission.EXPORTONLY);
        PackagePermission filterPermission = new PackagePermission(
                "(package.name=org.example.api)", PackagePermission.IMPORT);
        PermissionCollection permissionCollection = new PackagePermissionCollection();
        permissionCollection.add(grantedPermission);
        permissionCollection.add(filterPermission);

        PermissionCollection restoredPermissionCollection = serializeAndDeserialize(permissionCollection);

        assertThat(Collections.list(restoredPermissionCollection.elements()))
                .containsExactlyInAnyOrder(grantedPermission, filterPermission);
        assertThat(restoredPermissionCollection.implies(
                new PackagePermission("org.example.api", PackagePermission.EXPORTONLY))).isTrue();
        assertThat(restoredPermissionCollection.implies(
                new PackagePermission("org.example.api", PackagePermission.IMPORT))).isTrue();
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
