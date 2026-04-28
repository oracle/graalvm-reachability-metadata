/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mozilla.rhino;

import org.junit.jupiter.api.Test;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.FunctionObject;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

import java.lang.reflect.Field;
import java.security.Permission;

import static org.assertj.core.api.Assertions.assertThat;

public class FunctionObjectTest {
    @Test
    @SuppressWarnings("removal")
    void definesFunctionPropertiesWhenDeclaredMemberAccessIsDenied() throws Exception {
        Context cx = Context.enter();
        Field sawSecurityException = FunctionObject.class.getDeclaredField("sawSecurityException");
        sawSecurityException.setAccessible(true);
        boolean originalSawSecurityException = sawSecurityException.getBoolean(null);
        boolean restoreSawSecurityException = true;
        SecurityManager previousSecurityManager = System.getSecurityManager();
        boolean restoreSecurityManager = false;
        try {
            ScriptableObject scope = (ScriptableObject) cx.initStandardObjects();
            restoreSecurityManager = installSecurityManagerIfSupported(new DenyDeclaredMemberAccessSecurityManager());
            if (!restoreSecurityManager) {
                sawSecurityException.setBoolean(null, true);
            }

            scope.defineFunctionProperties(
                    new String[] {"echo"},
                    PublicFunctionLibrary.class,
                    ScriptableObject.EMPTY);

            if (restoreSecurityManager) {
                System.setSecurityManager(previousSecurityManager);
                restoreSecurityManager = false;
            }
            Object result = ScriptableObject.callMethod(cx, scope, "echo", new Object[] {"fallback"});

            assertThat(result).isEqualTo("echo:fallback");
        } finally {
            if (restoreSecurityManager) {
                System.setSecurityManager(previousSecurityManager);
            }
            if (restoreSawSecurityException) {
                sawSecurityException.setBoolean(null, originalSawSecurityException);
            }
            Context.exit();
        }
    }

    @Test
    void constructUsesDeclaringClassDefaultConstructorForMethodBackedFunction() throws Exception {
        Context cx = Context.enter();
        try {
            Scriptable scope = cx.initStandardObjects();
            FunctionObject function = new FunctionObject(
                    "record",
                    ConstructedByFunction.class.getMethod("record", String.class),
                    scope);

            Object result = function.construct(cx, scope, new Object[] {"created"});

            assertThat(result).isInstanceOf(ConstructedByFunction.class);
            ConstructedByFunction scriptable = (ConstructedByFunction) result;
            assertThat(scriptable.getValue()).isEqualTo("created");
            assertThat(scriptable.getParentScope()).isSameAs(scope);
        } finally {
            Context.exit();
        }
    }

    public static final class PublicFunctionLibrary {
        private PublicFunctionLibrary() {
        }

        public static String echo(String value) {
            return "echo:" + value;
        }
    }

    public static class ConstructedByFunction extends ScriptableObject {
        private static final long serialVersionUID = 1L;

        private String value;

        public ConstructedByFunction() {
        }

        @Override
        public String getClassName() {
            return "ConstructedByFunction";
        }

        public void record(String newValue) {
            value = newValue;
        }

        public String getValue() {
            return value;
        }
    }

    @SuppressWarnings("removal")
    private static boolean installSecurityManagerIfSupported(SecurityManager securityManager) {
        try {
            System.setSecurityManager(securityManager);
            return System.getSecurityManager() == securityManager;
        } catch (UnsupportedOperationException unsupportedOperationException) {
            return false;
        }
    }

    @SuppressWarnings("removal")
    private static final class DenyDeclaredMemberAccessSecurityManager extends SecurityManager {
        @Override
        public void checkPermission(Permission permission) {
            if ("accessDeclaredMembers".equals(permission.getName())) {
                throw new SecurityException("declared member access denied for fallback coverage");
            }
        }
    }
}
