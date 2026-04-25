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
import java.lang.reflect.Constructor;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.Permission;
import java.security.PermissionCollection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.osgi.framework.ServicePermission;

public class ServicePermissionCollection1Test {
    @Test
    void isolatedClassLoaderCreatesServicePermissionCollectionViaPublicApi()
            throws ReflectiveOperationException, IOException {
        URL libraryUrl = ServicePermission.class.getProtectionDomain().getCodeSource().getLocation();

        try (ChildFirstOsgiClassLoader classLoader = new ChildFirstOsgiClassLoader(libraryUrl)) {
            Class<?> servicePermissionClass =
                    Class.forName("org.osgi.framework.ServicePermission", true, classLoader);
            Constructor<?> constructor =
                    servicePermissionClass.getConstructor(String.class, String.class);
            Object permissionInstance =
                    constructor.newInstance("com.example.Service", ServicePermission.GET);
            Object collection =
                    servicePermissionClass.getMethod("newPermissionCollection")
                            .invoke(permissionInstance);

            assertThat(collection.getClass().getName())
                    .isEqualTo("org.osgi.framework.ServicePermissionCollection");
        }
    }

    @Test
    void serializationDescriptorUsesExpectedPersistentFieldTypes() {
        PermissionCollection collection =
                new ServicePermission("com.example.Service", ServicePermission.GET)
                        .newPermissionCollection();
        ObjectStreamClass descriptor = ObjectStreamClass.lookup(collection.getClass());
        ObjectStreamField permissionsField = descriptor.getField("permissions");
        ObjectStreamField allAllowedField = descriptor.getField("all_allowed");
        ObjectStreamField filterPermissionsField = descriptor.getField("filterPermissions");

        assertThat(descriptor.getName())
                .isEqualTo("org.osgi.framework.ServicePermissionCollection");
        assertThat(permissionsField.getType()).isEqualTo(Hashtable.class);
        assertThat(allAllowedField.getType()).isEqualTo(Boolean.TYPE);
        assertThat(filterPermissionsField.getType()).isEqualTo(HashMap.class);
    }

    @Test
    void serializedPermissionCollectionRetainsWildcardAndFilterServicePermissions()
            throws IOException, ClassNotFoundException {
        ServicePermission wildcardPermission =
                new ServicePermission("com.example.*", ServicePermission.GET);
        ServicePermission filterPermission =
                new ServicePermission("(objectClass=com.filtered.Service)", ServicePermission.GET);
        PermissionCollection collection = wildcardPermission.newPermissionCollection();

        collection.add(wildcardPermission);
        collection.add(filterPermission);

        PermissionCollection restoredCollection = roundTrip(collection);
        List<Permission> elements = Collections.list(restoredCollection.elements());

        assertThat(
                        restoredCollection.implies(
                                new ServicePermission("com.example.Service", ServicePermission.GET)))
                .isTrue();
        assertThat(
                        restoredCollection.implies(
                                new ServicePermission("com.filtered.Service", ServicePermission.GET)))
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

    private static final class ChildFirstOsgiClassLoader extends URLClassLoader {
        private ChildFirstOsgiClassLoader(URL libraryUrl) {
            super(new URL[] {libraryUrl}, ServicePermissionCollection1Test.class.getClassLoader());
        }

        @Override
        protected synchronized Class<?> loadClass(String name, boolean resolve)
                throws ClassNotFoundException {
            if (name.startsWith("org.osgi.")) {
                Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass == null) {
                    try {
                        loadedClass = findClass(name);
                    } catch (ClassNotFoundException ignored) {
                        loadedClass = super.loadClass(name, false);
                    }
                }
                if (resolve) {
                    resolveClass(loadedClass);
                }
                return loadedClass;
            }
            return super.loadClass(name, resolve);
        }
    }
}
