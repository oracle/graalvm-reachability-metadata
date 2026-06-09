/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javassist.javassist;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.net.URLClassLoader;
import java.security.Permission;

import javassist.util.proxy.FactoryHelper;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

@SuppressWarnings("removal")
public class SecurityActionsAnonymous3Test {
    @Test
    void factoryHelperFindsClassLoaderDefineClassMethodsWithSecurityManagerInstalled() throws Exception {
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

            URL testCodeSource = SecurityActionsAnonymous3Test.class.getProtectionDomain()
                    .getCodeSource()
                    .getLocation();
            URL libraryCodeSource = FactoryHelper.class.getProtectionDomain()
                    .getCodeSource()
                    .getLocation();

            try (ChildFirstJavassistLoader loader = new ChildFirstJavassistLoader(
                    testCodeSource, libraryCodeSource)) {
                Class<?> initializer = Class.forName(
                        IsolatedFactoryHelperInitializer.class.getName(), true, loader);

                assertThat(initializer.getName())
                        .isEqualTo(IsolatedFactoryHelperInitializer.class.getName());
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

    public static final class IsolatedFactoryHelperInitializer {
        static final int INTEGER_INDEX = FactoryHelper.typeIndex(int.class);

        private IsolatedFactoryHelperInitializer() {
        }
    }

    private static final class ChildFirstJavassistLoader extends URLClassLoader {
        private ChildFirstJavassistLoader(URL testCodeSource, URL libraryCodeSource) {
            super(new URL[] { testCodeSource, libraryCodeSource }, null);
        }

        @Override
        protected synchronized Class<?> loadClass(String name, boolean resolve)
                throws ClassNotFoundException {
            if (!name.startsWith("javassist.")) {
                return super.loadClass(name, resolve);
            }

            Class<?> loadedClass = findLoadedClass(name);
            if (loadedClass == null) {
                try {
                    loadedClass = findClass(name);
                } catch (ClassNotFoundException exception) {
                    loadedClass = super.loadClass(name, false);
                }
            }
            if (resolve) {
                resolveClass(loadedClass);
            }
            return loadedClass;
        }
    }
}
