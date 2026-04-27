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
import java.util.HashMap;
import org.junit.jupiter.api.Test;
import org.osgi.framework.CapabilityPermission;

public class CapabilityPermissionCollection1Test {
    @Test
    void serializationDescriptorUsesHashMapPersistentFields() {
        PermissionCollection collection =
                new CapabilityPermission("com.example.capability", CapabilityPermission.PROVIDE)
                        .newPermissionCollection();
        ObjectStreamClass descriptor = ObjectStreamClass.lookup(collection.getClass());
        ObjectStreamField permissionsField = descriptor.getField("permissions");
        ObjectStreamField filterPermissionsField = descriptor.getField("filterPermissions");

        assertThat(descriptor.getName())
                .isEqualTo("org.osgi.framework.CapabilityPermissionCollection");
        assertThat(permissionsField.getType()).isEqualTo(HashMap.class);
        assertThat(filterPermissionsField.getType()).isEqualTo(HashMap.class);
    }

    @Test
    void serializedPermissionCollectionAppliesWildcardCapabilityPermissions()
            throws IOException, ClassNotFoundException {
        CapabilityPermission permission =
                new CapabilityPermission("com.example.*", CapabilityPermission.PROVIDE);
        PermissionCollection collection = permission.newPermissionCollection();

        collection.add(permission);

        PermissionCollection restoredCollection = roundTrip(collection);
        Enumeration<Permission> elements = restoredCollection.elements();

        assertThat(
                        restoredCollection.implies(
                                new CapabilityPermission(
                                        "com.example.capability", CapabilityPermission.PROVIDE)))
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
