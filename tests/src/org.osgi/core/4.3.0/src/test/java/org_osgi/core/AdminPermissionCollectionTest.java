/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_osgi.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ObjectStreamClass;
import java.io.ObjectStreamField;
import java.lang.reflect.Method;
import java.security.Permission;
import java.security.PermissionCollection;
import java.util.Enumeration;
import java.util.Hashtable;

import org.junit.jupiter.api.Test;
import org.osgi.framework.AdminPermission;

public class AdminPermissionCollectionTest {
    @Test
    void newPermissionCollectionStartsEmpty() {
        PermissionCollection collection = createPermissionCollection();

        assertThat(collection.getClass().getName())
                .isEqualTo("org.osgi.framework.AdminPermissionCollection");
        assertThat(collection.elements().hasMoreElements()).isFalse();
    }

    @Test
    void addedAdminPermissionsAreEnumeratedAndMergedByName() {
        PermissionCollection collection = createPermissionCollection();
        AdminPermission classPermission = new AdminPermission("*", AdminPermission.CLASS);
        AdminPermission executePermission = new AdminPermission("*", AdminPermission.EXECUTE);

        collection.add(classPermission);
        collection.add(executePermission);

        Enumeration<Permission> elements = collection.elements();
        assertThat(elements.hasMoreElements()).isTrue();
        Permission mergedPermission = elements.nextElement();
        assertThat(elements.hasMoreElements()).isFalse();
        assertThat(mergedPermission.getName()).isEqualTo("*");
        assertThat(mergedPermission.getActions())
                .contains(AdminPermission.CLASS, AdminPermission.EXECUTE);
    }

    @Test
    void wildcardAdminPermissionsImplyRequestedActions() {
        PermissionCollection collection = createPermissionCollection();
        collection.add(new AdminPermission("*", AdminPermission.CLASS));

        assertThat(collection.implies(new AdminPermission("*", AdminPermission.CLASS))).isTrue();
        assertThat(collection.implies(new AdminPermission("*", AdminPermission.RESOLVE))).isTrue();
        assertThat(collection.implies(new AdminPermission("*", AdminPermission.EXECUTE))).isFalse();
    }

    @Test
    void serializationDescriptorUsesPersistentFieldTypes() {
        PermissionCollection collection = createPermissionCollection();

        ObjectStreamClass descriptor = ObjectStreamClass.lookup(collection.getClass());
        ObjectStreamField permissionsField = descriptor.getField("permissions");
        ObjectStreamField allAllowedField = descriptor.getField("all_allowed");

        assertThat(descriptor.getName())
                .isEqualTo("org.osgi.framework.AdminPermissionCollection");
        assertThat(permissionsField.getType()).isEqualTo(Hashtable.class);
        assertThat(allAllowedField.getType()).isEqualTo(Boolean.TYPE);
    }

    @Test
    void syntheticClassLookupResolvesPersistentFieldType() throws ReflectiveOperationException {
        PermissionCollection collection = createPermissionCollection();
        Class<?> collectionClass = collection.getClass();
        Method syntheticClassLookup = collectionClass.getDeclaredMethod("class$", String.class);
        syntheticClassLookup.setAccessible(true);

        assertThat(syntheticClassLookup.invoke(null, "java.util.Hashtable"))
                .isEqualTo(Hashtable.class);
    }

    private static PermissionCollection createPermissionCollection() {
        return new AdminPermission("*", AdminPermission.CLASS).newPermissionCollection();
    }
}
