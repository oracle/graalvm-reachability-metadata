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
import java.security.Permission;
import java.security.PermissionCollection;
import java.util.Enumeration;
import org.junit.jupiter.api.Test;
import org.osgi.framework.AdminPermission;

public class AdminPermissionCollection1Test {
    @Test
    void loadsAdminPermissionCollectionClass() throws ClassNotFoundException {
        assertThat(Class.forName("org.osgi.framework.AdminPermissionCollection").getName())
                .isEqualTo("org.osgi.framework.AdminPermissionCollection");
    }

    @Test
    void newPermissionCollectionStartsEmptyAndAppliesWildcardAdminPermission() {
        AdminPermission permission = new AdminPermission();
        PermissionCollection collection = permission.newPermissionCollection();

        assertThat(collection.getClass().getName())
                .isEqualTo("org.osgi.framework.AdminPermissionCollection");
        assertThat(collection.elements().hasMoreElements()).isFalse();

        collection.add(permission);

        assertThat(collection.implies(new AdminPermission())).isTrue();
    }

    @Test
    void serializedPermissionCollectionImpliesWildcardAdminPermission()
            throws IOException, ClassNotFoundException {
        AdminPermission permission = new AdminPermission();
        PermissionCollection collection = permission.newPermissionCollection();

        collection.add(permission);

        PermissionCollection restoredCollection = roundTrip(collection);
        Enumeration<Permission> elements = restoredCollection.elements();

        assertThat(restoredCollection.implies(new AdminPermission())).isTrue();
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
