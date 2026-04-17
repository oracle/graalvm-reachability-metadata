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
import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AdaptPermissionCollectionTest {
    @Test
    void loadsSerializedFieldTypeThroughCompatibilityHelper() {
        assertThat(AdaptPermissionCollection.class$("java.util.HashMap")).isEqualTo(HashMap.class);
    }

    @Test
    void createsCollectionAndReturnsAddedPermissions() {
        AdaptPermission grantedPermission = new AdaptPermission("*", AdaptPermission.ADAPT);
        PermissionCollection permissionCollection = grantedPermission.newPermissionCollection();

        permissionCollection.add(grantedPermission);
        List<Permission> permissions = Collections.list(permissionCollection.elements());

        assertThat(permissions).containsExactly(grantedPermission);
    }

    @Test
    void serializesAndDeserializesCollectionWithGrantedPermission() throws IOException, ClassNotFoundException {
        AdaptPermission grantedPermission = new AdaptPermission("*", AdaptPermission.ADAPT);
        PermissionCollection permissionCollection = grantedPermission.newPermissionCollection();
        permissionCollection.add(grantedPermission);

        PermissionCollection restoredPermissionCollection = serializeAndDeserialize(permissionCollection);

        assertThat(Collections.list(restoredPermissionCollection.elements()))
                .singleElement()
                .isEqualTo(grantedPermission);
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
