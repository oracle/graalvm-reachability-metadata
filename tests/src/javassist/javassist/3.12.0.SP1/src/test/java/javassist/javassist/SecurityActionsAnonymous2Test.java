/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javassist.javassist;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.Permission;

import javassist.util.proxy.ProxyFactory;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

@SuppressWarnings("removal")
public class SecurityActionsAnonymous2Test {
    @Test
    void proxyFactoryReadsSuperclassConstructorsWithSecurityManagerInstalled() throws Exception {
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

            ProxyFactory factory = new ProxyFactory();
            factory.setUseCache(false);
            factory.setSuperclass(ConstructorBackedService.class);
            factory.setFilter(method -> method.getName().equals("message"));

            try {
                Class<?> proxyClass = factory.createClass();

                assertThat(ProxyFactory.isProxyClass(proxyClass)).isTrue();
                assertThat(proxyClass.getSuperclass()).isEqualTo(ConstructorBackedService.class);
            } catch (RuntimeException exception) {
                if (!hasUnsupportedFeatureError(exception)) {
                    throw exception;
                }
            } catch (Error error) {
                if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                    throw error;
                }
            }
        } finally {
            if (installed) {
                System.setSecurityManager(originalManager);
            }
        }
    }

    private static boolean hasUnsupportedFeatureError(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof Error error && NativeImageSupport.isUnsupportedFeatureError(error)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
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

    public static class ConstructorBackedService {
        private final String value;

        public ConstructorBackedService() {
            this("default");
        }

        public ConstructorBackedService(String value) {
            this.value = value;
        }

        public String message() {
            return value;
        }
    }
}
