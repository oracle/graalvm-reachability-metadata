/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_osgi.core;

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
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;

import org.junit.jupiter.api.Test;
import org.osgi.framework.PackagePermission;

public class PackagePermissionCollectionTest {
    @Test
    void newPermissionCollectionCreatesEmptyPackagePermissionCollection() {
        PermissionCollection collection = createPermissionCollection();

        assertThat(collection.getClass().getName())
                .isEqualTo("org.osgi.framework.PackagePermissionCollection");
        assertThat(collection.elements().hasMoreElements()).isFalse();
    }

    @Test
    void addedPackagePermissionsAreEnumeratedAndMergedByName() {
        PermissionCollection collection = createPermissionCollection();
        PackagePermission importPermission = new PackagePermission(
                "org.example.package",
                PackagePermission.IMPORT);
        PackagePermission exportPermission = new PackagePermission(
                "org.example.package",
                PackagePermission.EXPORTONLY);

        collection.add(importPermission);
        collection.add(exportPermission);

        Enumeration<Permission> elements = collection.elements();
        assertThat(elements.hasMoreElements()).isTrue();
        Permission mergedPermission = elements.nextElement();
        assertThat(elements.hasMoreElements()).isFalse();
        assertThat(mergedPermission.getName()).isEqualTo("org.example.package");
        assertThat(mergedPermission.getActions())
                .contains(PackagePermission.IMPORT, PackagePermission.EXPORTONLY);
    }

    @Test
    void wildcardAndFilterPackagePermissionsImplyMatchingRequests() {
        PermissionCollection collection = createPermissionCollection();
        collection.add(new PackagePermission(
                "org.example.*",
                PackagePermission.EXPORTONLY));
        collection.add(new PackagePermission(
                "(package.name=org.example.filtered)",
                PackagePermission.IMPORT));

        assertThat(collection.implies(new PackagePermission(
                "org.example.package",
                PackagePermission.EXPORTONLY))).isTrue();
        assertThat(collection.implies(new PackagePermission(
                "org.example.filtered",
                PackagePermission.IMPORT))).isTrue();
        assertThat(collection.implies(new PackagePermission(
                "org.other.package",
                PackagePermission.IMPORT))).isFalse();
    }

    @Test
    void serializationDescriptorUsesPersistentFieldTypes() {
        PermissionCollection collection = createPermissionCollection();

        ObjectStreamClass descriptor = ObjectStreamClass.lookup(
                collection.getClass());
        ObjectStreamField permissionsField = descriptor.getField("permissions");
        ObjectStreamField allAllowedField = descriptor.getField("all_allowed");
        ObjectStreamField filterPermissionsField = descriptor.getField(
                "filterPermissions");

        assertThat(descriptor.getName())
                .isEqualTo("org.osgi.framework.PackagePermissionCollection");
        assertThat(permissionsField.getType()).isEqualTo(Hashtable.class);
        assertThat(allAllowedField.getType()).isEqualTo(Boolean.TYPE);
        assertThat(filterPermissionsField.getType()).isEqualTo(HashMap.class);
    }

    @Test
    void syntheticClassLookupResolvesPersistentFieldTypes()
            throws ReflectiveOperationException {
        PermissionCollection collection = createPermissionCollection();
        Class<?> collectionClass = collection.getClass();
        Method syntheticClassLookup = collectionClass.getDeclaredMethod(
                "class$",
                String.class);
        syntheticClassLookup.setAccessible(true);

        assertThat(syntheticClassLookup.invoke(null, "java.util.Hashtable"))
                .isEqualTo(Hashtable.class);
        assertThat(syntheticClassLookup.invoke(null, "java.util.HashMap"))
                .isEqualTo(HashMap.class);
    }

    @Test
    void serializedPermissionCollectionRetainsNamedAndFilterPermissions()
            throws IOException, ClassNotFoundException {
        PermissionCollection collection = createPermissionCollection();
        collection.add(new PackagePermission("*", PackagePermission.EXPORTONLY));
        collection.add(new PackagePermission(
                "(package.name=org.example.filtered)",
                PackagePermission.IMPORT));

        PermissionCollection deserializedCollection = deserialize(serialize(collection));

        assertThat(deserializedCollection.implies(new PackagePermission(
                "org.example.any",
                PackagePermission.EXPORTONLY))).isTrue();
        assertThat(deserializedCollection.implies(new PackagePermission(
                "org.example.filtered",
                PackagePermission.IMPORT))).isTrue();
        assertThat(deserializedCollection.implies(new PackagePermission(
                "org.example.any",
                PackagePermission.IMPORT))).isFalse();
    }

    private static PermissionCollection createPermissionCollection() {
        return new PackagePermission(
                "*",
                PackagePermission.IMPORT).newPermissionCollection();
    }

    private static byte[] serialize(PermissionCollection collection) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutput = new ObjectOutputStream(output)) {
            objectOutput.writeObject(collection);
        }
        return output.toByteArray();
    }

    private static PermissionCollection deserialize(byte[] serializedCollection)
            throws IOException, ClassNotFoundException {
        ByteArrayInputStream input = new ByteArrayInputStream(serializedCollection);
        try (ObjectInputStream objectInput = new ObjectInputStream(input)) {
            return (PermissionCollection) objectInput.readObject();
        }
    }
}
