/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat.jasper_runtime;

import org.apache.jasper.runtime.ProtectedFunctionMapper;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.security.Permission;
import java.security.PrivilegedExceptionAction;

import static org.assertj.core.api.Assertions.assertThat;

public class ProtectedFunctionMapperAnonymous4Test {
    @Test
    @SuppressWarnings("removal")
    void getMapForFunctionResolvesDeclaredMethodThroughPrivilegedExceptionAction() throws Throwable {
        final String previousPackageDefinition = System.getProperty("package.definition");
        System.setProperty("package.definition", "true");
        final SecurityManager previousSecurityManager = System.getSecurityManager();
        final boolean securityManagerInstalled = installPermissiveSecurityManager(previousSecurityManager);

        try {
            final Method method;
            if (securityManagerInstalled || previousSecurityManager != null) {
                method = resolveFunctionThroughSingleFunctionMapper();
            } else {
                method = runSingleFunctionMapperPrivilegedExceptionActionDirectly();
            }

            assertThat(method).isNotNull();
            assertThat(method.getDeclaringClass()).isEqualTo(FunctionLibrary.class);
            assertThat(method.getName()).isEqualTo("repeat");
            assertThat(method.getParameterTypes()).containsExactly(String.class, int.class);
        } finally {
            if (securityManagerInstalled) {
                System.setSecurityManager(previousSecurityManager);
            }
            restorePackageDefinition(previousPackageDefinition);
        }
    }

    private static Method resolveFunctionThroughSingleFunctionMapper() {
        final ProtectedFunctionMapper mapper = ProtectedFunctionMapper.getMapForFunction(
                "fn:repeat", FunctionLibrary.class, "repeat", new Class[] {String.class, int.class});
        return mapper.resolveFunction("fn", "repeat");
    }

    private static Method runSingleFunctionMapperPrivilegedExceptionActionDirectly() throws Throwable {
        final Class<?> actionType = Class.forName("org.apache.jasper.runtime.ProtectedFunctionMapper$4");
        final MethodHandles.Lookup actionLookup = MethodHandles.privateLookupIn(actionType, MethodHandles.lookup());
        final MethodHandle actionConstructor = actionLookup.findConstructor(actionType, MethodType.methodType(
                void.class, Class.class, String.class, Class[].class));
        final PrivilegedExceptionAction<?> action = (PrivilegedExceptionAction<?>) actionConstructor.invoke(
                FunctionLibrary.class,
                "repeat",
                new Class[] {String.class, int.class});
        return (Method) action.run();
    }

    @SuppressWarnings("removal")
    private static boolean installPermissiveSecurityManager(final SecurityManager previousSecurityManager) {
        if (previousSecurityManager != null) {
            return false;
        }
        try {
            System.setSecurityManager(new PermissiveSecurityManager());
            return true;
        } catch (final UnsupportedOperationException unsupportedOperationException) {
            return false;
        }
    }

    private static void restorePackageDefinition(final String previousPackageDefinition) {
        if (previousPackageDefinition == null) {
            System.clearProperty("package.definition");
        } else {
            System.setProperty("package.definition", previousPackageDefinition);
        }
    }

    public static class FunctionLibrary {
        public static String repeat(final String value, final int count) {
            final StringBuilder result = new StringBuilder(value.length() * count);
            for (int index = 0; index < count; index++) {
                result.append(value);
            }
            return result.toString();
        }
    }

    @SuppressWarnings("removal")
    private static final class PermissiveSecurityManager extends SecurityManager {
        @Override
        public void checkPermission(final Permission permission) {
        }

        @Override
        public void checkPermission(final Permission permission, final Object context) {
        }
    }
}
