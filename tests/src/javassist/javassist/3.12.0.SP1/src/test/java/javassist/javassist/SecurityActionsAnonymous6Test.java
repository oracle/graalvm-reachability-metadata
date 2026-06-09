/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javassist.javassist;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.security.Permission;

import org.junit.jupiter.api.Test;

@SuppressWarnings("removal")
public class SecurityActionsAnonymous6Test {
    @Test
    void setsFieldValueWithSecurityManagerInstalled() throws Throwable {
        SecurityManager originalManager = System.getSecurityManager();
        SecurityManager manager = null;
        boolean installed = false;
        try {
            try {
                manager = new PermissiveSecurityManager();
                System.setSecurityManager(manager);
                installed = System.getSecurityManager() == manager;
            } catch (UnsupportedOperationException exception) {
                assertThat(exception).isInstanceOf(UnsupportedOperationException.class);
            }

            Field field = FieldTarget.class.getField("value");
            FieldTarget target = new FieldTarget("initial");

            setField(field, target, "updated");

            assertThat(target.value).isEqualTo("updated");
        } finally {
            if (installed) {
                System.setSecurityManager(originalManager);
            }
        }
    }

    private static void setField(Field field, Object target, Object value) throws Throwable {
        Class<?> securityActions = Class.forName("javassist.util.proxy.SecurityActions");
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                securityActions, MethodHandles.lookup());
        MethodHandle set = lookup.findStatic(
                securityActions, "set",
                MethodType.methodType(void.class, Field.class, Object.class, Object.class));
        set.invoke(field, target, value);
    }

    private static final class PermissiveSecurityManager extends SecurityManager {
        @Override
        public void checkPermission(Permission permission) {
            // Permit all operations while the Javassist security-manager branch is exercised.
        }

        @Override
        public void checkPermission(Permission permission, Object context) {
            // Permit all operations while the Javassist security-manager branch is exercised.
        }
    }

    public static final class FieldTarget {
        public Object value;

        public FieldTarget(Object value) {
            this.value = value;
        }
    }
}
