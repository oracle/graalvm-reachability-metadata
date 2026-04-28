/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javassist_javassist;

import java.lang.reflect.Method;
import java.security.Permission;

import javassist.util.proxy.RuntimeSupport;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SecurityActionsAnonymous1Test {
    @Test
    @SuppressWarnings("removal")
    void findMethodUsesPrivilegedDeclaredMethodsLookupWhenSecurityManagerIsPresent() {
        SecurityManager previousSecurityManager = System.getSecurityManager();
        boolean securityManagerInstalled = installSecurityManagerIfSupported();

        try {
            String descriptor = RuntimeSupport.makeDescriptor(new Class[0], String.class);
            Method method = RuntimeSupport.findMethod(new LookupTarget(), "message", descriptor);

            assertThat(method.getName()).isEqualTo("message");
            assertThat(method.getDeclaringClass()).isEqualTo(LookupTarget.class);
        } finally {
            if (securityManagerInstalled) {
                System.setSecurityManager(previousSecurityManager);
            }
        }
    }

    @SuppressWarnings("removal")
    private static boolean installSecurityManagerIfSupported() {
        try {
            System.setSecurityManager(new PermissiveSecurityManager());
            return System.getSecurityManager() != null;
        } catch (UnsupportedOperationException unsupportedOperationException) {
            return false;
        }
    }

    public static class LookupTarget {
        public String message() {
            return "hello";
        }
    }

    @SuppressWarnings("removal")
    private static final class PermissiveSecurityManager extends SecurityManager {
        @Override
        public void checkPermission(Permission permission) {
        }
    }
}
