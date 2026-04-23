/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_osgi.org_osgi_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.ObjectStreamField;
import java.security.Permission;
import java.security.PermissionCollection;
import java.util.Enumeration;
import java.util.Hashtable;
import org.junit.jupiter.api.Test;
import org.osgi.framework.BundlePermission;

public class BundlePermissionCollection1Test {
    @Test
    void serializationDescriptorUsesDeclaredPersistentFields() {
        PermissionCollection collection =
                new BundlePermission("com.example.bundle", BundlePermission.REQUIRE)
                        .newPermissionCollection();
        ObjectStreamClass descriptor = ObjectStreamClass.lookup(collection.getClass());
        ObjectStreamField permissionsField = descriptor.getField("permissions");
        ObjectStreamField allAllowedField = descriptor.getField("all_allowed");

        assertThat(descriptor.getName()).isEqualTo("org.osgi.framework.BundlePermissionCollection");
        assertThat(permissionsField.getType()).isEqualTo(Hashtable.class);
        assertThat(allAllowedField.getType()).isEqualTo(boolean.class);
    }

    @Test
    void newPermissionCollectionStartsEmptyAndUsesBundlePermissionCollectionType() {
        BundlePermission permission =
                new BundlePermission("com.example.bundle", BundlePermission.REQUIRE);
        PermissionCollection collection = permission.newPermissionCollection();

        assertThat(collection.getClass().getName())
                .isEqualTo("org.osgi.framework.BundlePermissionCollection");
        assertThat(collection.elements().hasMoreElements()).isFalse();

        collection.add(permission);

        assertThat(
                        collection.implies(
                                new BundlePermission(
                                        "com.example.bundle", BundlePermission.REQUIRE)))
                .isTrue();
    }

    @Test
    void serializedPermissionCollectionAppliesWildcardBundlePermissions()
            throws IOException, ClassNotFoundException {
        BundlePermission permission = new BundlePermission("com.example.*", BundlePermission.REQUIRE);
        PermissionCollection collection = permission.newPermissionCollection();

        collection.add(permission);

        PermissionCollection restoredCollection = roundTrip(collection);
        Enumeration<Permission> elements = restoredCollection.elements();

        assertThat(restoredCollection.getClass().getName())
                .isEqualTo("org.osgi.framework.BundlePermissionCollection");
        assertThat(
                        restoredCollection.implies(
                                new BundlePermission("com.example.bundle", BundlePermission.REQUIRE)))
                .isTrue();
        assertThat(elements.hasMoreElements()).isTrue();
        assertThat(elements.nextElement()).isEqualTo(permission);
    }

    private PermissionCollection roundTrip(PermissionCollection collection)
            throws IOException, ClassNotFoundException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutput = new ObjectOutputStream(output)) {
            objectOutput.writeObject(collection);
        }

        try (ObjectInputStream objectInput =
                new ObjectInputStream(new ByteArrayInputStream(output.toByteArray()))) {
            return (PermissionCollection) objectInput.readObject();
        }
    }
}
