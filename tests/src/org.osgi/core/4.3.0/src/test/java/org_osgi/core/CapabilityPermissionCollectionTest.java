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
import java.io.ObjectStreamClass;
import java.io.ObjectStreamField;
import java.security.Permission;
import java.security.PermissionCollection;
import java.util.Enumeration;
import java.util.HashMap;

import org.junit.jupiter.api.Test;
import org.osgi.framework.CapabilityPermission;

public class CapabilityPermissionCollectionTest {
    @Test
    void newPermissionCollectionCreatesEmptyCapabilityPermissionCollection() {
        PermissionCollection collection = createPermissionCollection();

        assertThat(collection.getClass().getName())
                .isEqualTo("org.osgi.framework.CapabilityPermissionCollection");
        assertThat(collection.elements().hasMoreElements()).isFalse();
    }

    @Test
    void addedCapabilityPermissionsAreEnumeratedAndMergedByName() {
        PermissionCollection collection = createPermissionCollection();
        CapabilityPermission requirePermission = new CapabilityPermission(
                "org.example.capability",
                CapabilityPermission.REQUIRE);
        CapabilityPermission providePermission = new CapabilityPermission(
                "org.example.capability",
                CapabilityPermission.PROVIDE);

        collection.add(requirePermission);
        collection.add(providePermission);

        Enumeration<Permission> elements = collection.elements();
        assertThat(elements.hasMoreElements()).isTrue();
        Permission mergedPermission = elements.nextElement();
        assertThat(elements.hasMoreElements()).isFalse();
        assertThat(mergedPermission.getName()).isEqualTo("org.example.capability");
        assertThat(mergedPermission.getActions())
                .contains(CapabilityPermission.REQUIRE, CapabilityPermission.PROVIDE);
    }

    @Test
    void wildcardAndFilterCapabilityPermissionsImplyMatchingRequests() {
        PermissionCollection collection = createPermissionCollection();
        collection.add(new CapabilityPermission("org.example.*", CapabilityPermission.PROVIDE));
        collection.add(new CapabilityPermission(
                "(capability.namespace=org.example.filtered)",
                CapabilityPermission.REQUIRE));

        assertThat(collection.implies(new CapabilityPermission(
                "org.example.capability",
                CapabilityPermission.PROVIDE))).isTrue();
        assertThat(collection.implies(new CapabilityPermission(
                "org.example.filtered",
                CapabilityPermission.REQUIRE))).isTrue();
        assertThat(collection.implies(new CapabilityPermission(
                "org.other.capability",
                CapabilityPermission.REQUIRE))).isFalse();
    }

    @Test
    void serializationDescriptorUsesPersistentFieldTypes() {
        PermissionCollection collection = createPermissionCollection();

        ObjectStreamClass descriptor = ObjectStreamClass.lookup(collection.getClass());
        ObjectStreamField permissionsField = descriptor.getField("permissions");
        ObjectStreamField allAllowedField = descriptor.getField("all_allowed");
        ObjectStreamField filterPermissionsField = descriptor.getField("filterPermissions");

        assertThat(descriptor.getName())
                .isEqualTo("org.osgi.framework.CapabilityPermissionCollection");
        assertThat(permissionsField.getType()).isEqualTo(HashMap.class);
        assertThat(allAllowedField.getType()).isEqualTo(Boolean.TYPE);
        assertThat(filterPermissionsField.getType()).isEqualTo(HashMap.class);
    }

    @Test
    void serializedPermissionCollectionWritesAddedPermissions() throws IOException {
        PermissionCollection collection = createPermissionCollection();
        collection.add(new CapabilityPermission("*", CapabilityPermission.REQUIRE));
        collection.add(new CapabilityPermission("org.example.*", CapabilityPermission.PROVIDE));

        byte[] serializedCollection = serialize(collection);

        assertThat(serializedCollection).isNotEmpty();
    }

    private static PermissionCollection createPermissionCollection() {
        return new CapabilityPermission("*", CapabilityPermission.REQUIRE).newPermissionCollection();
    }

    private static byte[] serialize(PermissionCollection collection) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutput = new ObjectOutputStream(output)) {
            objectOutput.writeObject(collection);
        }
        return output.toByteArray();
    }
}
