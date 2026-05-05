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
import org.osgi.framework.ServicePermission;

public class ServicePermissionCollectionTest {
    @Test
    void newPermissionCollectionCreatesEmptyServicePermissionCollection() {
        PermissionCollection collection = createPermissionCollection();

        assertThat(collection.getClass().getName())
                .isEqualTo("org.osgi.framework.ServicePermissionCollection");
        assertThat(collection.elements().hasMoreElements()).isFalse();
    }

    @Test
    void addedServicePermissionsAreEnumeratedAndMergedByName() {
        PermissionCollection collection = createPermissionCollection();
        ServicePermission getPermission = new ServicePermission(
                "org.example.Service",
                ServicePermission.GET);
        ServicePermission registerPermission = new ServicePermission(
                "org.example.Service",
                ServicePermission.REGISTER);

        collection.add(getPermission);
        collection.add(registerPermission);

        Enumeration<Permission> elements = collection.elements();
        assertThat(elements.hasMoreElements()).isTrue();
        Permission mergedPermission = elements.nextElement();
        assertThat(elements.hasMoreElements()).isFalse();
        assertThat(mergedPermission.getName()).isEqualTo("org.example.Service");
        assertThat(mergedPermission.getActions())
                .contains(ServicePermission.GET, ServicePermission.REGISTER);
    }

    @Test
    void wildcardAndFilterServicePermissionsImplyMatchingRequests() {
        PermissionCollection collection = createPermissionCollection();
        collection.add(new ServicePermission("org.example.*", ServicePermission.REGISTER));
        collection.add(new ServicePermission(
                "(objectClass=org.example.FilteredService)",
                ServicePermission.GET));

        assertThat(collection.implies(new ServicePermission(
                "org.example.RegisteredService",
                ServicePermission.REGISTER))).isTrue();
        assertThat(collection.implies(new ServicePermission(
                "org.example.FilteredService",
                ServicePermission.GET))).isTrue();
        assertThat(collection.implies(new ServicePermission(
                "org.other.Service",
                ServicePermission.GET))).isFalse();
    }

    @Test
    void serializationDescriptorUsesPersistentFieldTypes() {
        PermissionCollection collection = createPermissionCollection();

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
        assertThat(syntheticClassLookup.invoke(
                null,
                "org.osgi.framework.ServicePermission"))
                .isEqualTo(ServicePermission.class);
    }

    @Test
    void serializedPermissionCollectionRetainsNamedWildcardAndFilterPermissions()
            throws IOException, ClassNotFoundException {
        PermissionCollection collection = createPermissionCollection();
        collection.add(new ServicePermission("*", ServicePermission.GET));
        collection.add(new ServicePermission("org.example.*", ServicePermission.REGISTER));
        collection.add(new ServicePermission(
                "(objectClass=org.example.FilteredService)",
                ServicePermission.GET));

        PermissionCollection deserializedCollection = deserialize(serialize(collection));

        assertThat(deserializedCollection.implies(new ServicePermission(
                "org.any.Service",
                ServicePermission.GET))).isTrue();
        assertThat(deserializedCollection.implies(new ServicePermission(
                "org.example.RegisteredService",
                ServicePermission.REGISTER))).isTrue();
        assertThat(deserializedCollection.implies(new ServicePermission(
                "org.example.FilteredService",
                ServicePermission.GET))).isTrue();
        assertThat(deserializedCollection.implies(new ServicePermission(
                "org.other.Service",
                ServicePermission.REGISTER))).isFalse();
    }

    private static PermissionCollection createPermissionCollection() {
        return new ServicePermission("*", ServicePermission.GET).newPermissionCollection();
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
