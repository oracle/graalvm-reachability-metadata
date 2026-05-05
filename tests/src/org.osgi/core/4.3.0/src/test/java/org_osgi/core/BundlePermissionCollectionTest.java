/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_osgi.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.security.Permission;
import java.security.PermissionCollection;
import java.util.Enumeration;

import org.junit.jupiter.api.Test;
import org.osgi.framework.BundlePermission;

public class BundlePermissionCollectionTest {
    @Test
    void newPermissionCollectionCreatesEmptyBundlePermissionCollection() {
        PermissionCollection collection = createPermissionCollection();

        assertThat(collection.getClass().getName())
                .isEqualTo("org.osgi.framework.BundlePermissionCollection");
        assertThat(collection.elements().hasMoreElements()).isFalse();
    }

    @Test
    void addedBundlePermissionsAreEnumeratedAndMergedByName() {
        PermissionCollection collection = createPermissionCollection();
        BundlePermission requirePermission = new BundlePermission("org.example.bundle", BundlePermission.REQUIRE);
        BundlePermission hostPermission = new BundlePermission("org.example.bundle", BundlePermission.HOST);

        collection.add(requirePermission);
        collection.add(hostPermission);

        Enumeration<Permission> elements = collection.elements();
        assertThat(elements.hasMoreElements()).isTrue();
        Permission mergedPermission = elements.nextElement();
        assertThat(elements.hasMoreElements()).isFalse();
        assertThat(mergedPermission.getName()).isEqualTo("org.example.bundle");
        assertThat(mergedPermission.getActions())
                .contains(BundlePermission.REQUIRE, BundlePermission.HOST);
    }

    @Test
    void wildcardBundlePermissionImpliesHierarchicalNamesAndProvideImpliesRequire() {
        PermissionCollection collection = createPermissionCollection();
        collection.add(new BundlePermission("org.example.*", BundlePermission.PROVIDE));
        collection.add(new BundlePermission("org.example.bundle", BundlePermission.HOST));

        assertThat(collection.implies(new BundlePermission("org.example.bundle", BundlePermission.REQUIRE))).isTrue();
        assertThat(collection.implies(new BundlePermission("org.example.bundle", BundlePermission.PROVIDE))).isTrue();
        assertThat(collection.implies(new BundlePermission("org.example.bundle", BundlePermission.HOST))).isTrue();
        assertThat(collection.implies(new BundlePermission("org.other.bundle", BundlePermission.REQUIRE))).isFalse();
    }

    @Test
    void serializedPermissionCollectionWritesAddedPermissions() throws IOException {
        PermissionCollection collection = createPermissionCollection();
        collection.add(new BundlePermission("*", BundlePermission.FRAGMENT));
        collection.add(new BundlePermission("org.example.*", BundlePermission.HOST));

        byte[] serializedCollection = serialize(collection);

        assertThat(serializedCollection).isNotEmpty();
    }

    @Test
    void syntheticClassLookupResolvesApplicationClassName() throws Throwable {
        PermissionCollection collection = createPermissionCollection();
        Class<?> collectionClass = collection.getClass();
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(collectionClass, MethodHandles.lookup());
        MethodHandle syntheticClassLookup = lookup.findStatic(
                collectionClass,
                "class$",
                MethodType.methodType(Class.class, String.class));

        Class<?> resolvedClass = (Class<?>) syntheticClassLookup.invoke(LookupTarget.class.getName());

        assertThat(resolvedClass).isEqualTo(LookupTarget.class);
    }

    public static final class LookupTarget {
    }

    private static PermissionCollection createPermissionCollection() {
        return new BundlePermission("*", BundlePermission.REQUIRE).newPermissionCollection();
    }

    private static byte[] serialize(PermissionCollection collection) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutput = new ObjectOutputStream(output)) {
            objectOutput.writeObject(collection);
        }
        return output.toByteArray();
    }

}
