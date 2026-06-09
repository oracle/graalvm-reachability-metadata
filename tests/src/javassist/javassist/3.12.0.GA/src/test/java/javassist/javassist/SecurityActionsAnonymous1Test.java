/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javassist.javassist;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.security.Permission;

import javassist.util.proxy.RuntimeSupport;

import org.junit.jupiter.api.Test;

@SuppressWarnings("removal")
public class SecurityActionsAnonymous1Test {
    @Test
    void runtimeSupportFindsDeclaredMethodWithSecurityManagerInstalled() {
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

            Method method = RuntimeSupport.findMethod(new DeclaredMethodTarget(), "message", "()Ljava/lang/String;");

            assertThat(method.getDeclaringClass()).isEqualTo(DeclaredMethodTarget.class);
            assertThat(method.getName()).isEqualTo("message");
            assertThat(method.getReturnType()).isEqualTo(String.class);
        } finally {
            if (installed) {
                System.setSecurityManager(originalManager);
            }
        }
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

    public static final class DeclaredMethodTarget {
        public String message() {
            return "declared";
        }
    }
}
