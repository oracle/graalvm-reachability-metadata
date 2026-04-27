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
import java.lang.reflect.Method;
import java.security.Permission;
import java.security.PermissionCollection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.osgi.framework.PackagePermission;

public class PackagePermissionCollection1Test {
    @Test
    void syntheticClassLookupResolvesPersistentFieldTypes() throws ReflectiveOperationException {
        Class<?> collectionClass = Class.forName("org.osgi.framework.PackagePermissionCollection");
        Method syntheticClassLookup = collectionClass.getDeclaredMethod("class$", String.class);
        syntheticClassLookup.setAccessible(true);

        assertThat(syntheticClassLookup.invoke(null, "java.util.Hashtable"))
                .isEqualTo(Hashtable.class);
        assertThat(syntheticClassLookup.invoke(null, "java.util.HashMap")).isEqualTo(HashMap.class);
    }

    @Test
    void serializationDescriptorUsesExpectedPersistentFieldTypes() {
        PermissionCollection collection =
                new PackagePermission("com.example.package", PackagePermission.IMPORT)
                        .newPermissionCollection();
        ObjectStreamClass descriptor = ObjectStreamClass.lookup(collection.getClass());
        ObjectStreamField permissionsField = descriptor.getField("permissions");
        ObjectStreamField allAllowedField = descriptor.getField("all_allowed");
        ObjectStreamField filterPermissionsField = descriptor.getField("filterPermissions");

        assertThat(descriptor.getName())
                .isEqualTo("org.osgi.framework.PackagePermissionCollection");
        assertThat(permissionsField.getType()).isEqualTo(Hashtable.class);
        assertThat(allAllowedField.getType()).isEqualTo(Boolean.TYPE);
        assertThat(filterPermissionsField.getType()).isEqualTo(HashMap.class);
    }

    @Test
    void serializedPermissionCollectionRetainsWildcardAndFilterPackagePermissions()
            throws IOException, ClassNotFoundException {
        PackagePermission wildcardPermission =
                new PackagePermission("com.example.*", PackagePermission.IMPORT);
        PackagePermission filterPermission =
                new PackagePermission("(package.name=com.filtered.package)", PackagePermission.IMPORT);
        PermissionCollection collection = wildcardPermission.newPermissionCollection();

        collection.add(wildcardPermission);
        collection.add(filterPermission);

        PermissionCollection restoredCollection = roundTrip(collection);
        List<Permission> elements = Collections.list(restoredCollection.elements());

        assertThat(
                        restoredCollection.implies(
                                new PackagePermission("com.example.package", PackagePermission.IMPORT)))
                .isTrue();
        assertThat(
                        restoredCollection.implies(
                                new PackagePermission(
                                        "com.filtered.package", PackagePermission.IMPORT)))
                .isTrue();
        assertThat(elements).containsExactlyInAnyOrder(wildcardPermission, filterPermission);
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
