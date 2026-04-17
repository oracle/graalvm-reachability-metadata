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
import java.util.Hashtable;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BundlePermissionCollectionTest {
    @Test
    void createsCollectionAndReturnsAddedPermissions() {
        BundlePermission grantedPermission = new BundlePermission("*", BundlePermission.PROVIDE);
        PermissionCollection permissionCollection = new BundlePermissionCollection();

        permissionCollection.add(grantedPermission);
        List<Permission> permissions = Collections.list(permissionCollection.elements());

        assertThat(permissions).containsExactly(grantedPermission);
    }

    @Test
    void impliesRequestedPermissionFromWildcardBundlePermission() {
        PermissionCollection permissionCollection = new BundlePermissionCollection();
        permissionCollection.add(new BundlePermission("org.example.*", BundlePermission.PROVIDE));

        boolean implied = permissionCollection.implies(
                new BundlePermission("org.example.bundle", BundlePermission.REQUIRE));

        assertThat(implied).isTrue();
    }

    @Test
    void resolvesHashtableClassThroughSyntheticClassLookup() {
        assertThat(BundlePermissionCollection.class$("java.util.Hashtable")).isEqualTo(Hashtable.class);
    }

    @Test
    void serializesAndDeserializesCollectionWithGrantedPermission() throws IOException, ClassNotFoundException {
        BundlePermission grantedPermission = new BundlePermission("org.example.bundle", BundlePermission.HOST);
        PermissionCollection permissionCollection = new BundlePermissionCollection();
        permissionCollection.add(grantedPermission);

        PermissionCollection restoredPermissionCollection = serializeAndDeserialize(permissionCollection);

        assertThat(Collections.list(restoredPermissionCollection.elements()))
                .singleElement()
                .isEqualTo(grantedPermission);
        assertThat(restoredPermissionCollection.implies(
                new BundlePermission("org.example.bundle", BundlePermission.HOST))).isTrue();
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
