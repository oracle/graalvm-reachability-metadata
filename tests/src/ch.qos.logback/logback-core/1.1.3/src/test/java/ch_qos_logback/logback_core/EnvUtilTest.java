/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_logback.logback_core;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.core.util.EnvUtil;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;
import java.util.ServiceLoader;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;

public class EnvUtilTest {

    private static final String ISOLATED_PROVIDER_PACKAGE = "ch_qos_logback.logback_core.isolated.";
    private static final String JANINO_PACKAGE = "org.codehaus.janino.";
    private static final String LOGBACK_PACKAGE = "ch.qos.logback.";

    @Test
    void reportsJaninoAvailabilityWhenJaninoIsResolvableFromTheEnvUtilClassLoader() throws Exception {
        try (ChildFirstClassLoader classLoader = new ChildFirstClassLoader(new URL[] {
                codeSourceUrl(EnvUtilTest.class),
                codeSourceUrl(EnvUtil.class)
        }, EnvUtilTest.class.getClassLoader())) {
            BooleanSupplier action = ServiceLoader.load(BooleanSupplier.class, classLoader)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Expected an isolated EnvUtil provider"));

            assertThat(action.getAsBoolean()).isTrue();
        }
    }

    private static URL codeSourceUrl(Class<?> type) {
        CodeSource codeSource = type.getProtectionDomain().getCodeSource();

        assertThat(codeSource).isNotNull();
        return codeSource.getLocation();
    }

    private static final class ChildFirstClassLoader extends URLClassLoader {

        private ChildFirstClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass == null) {
                    if (isChildFirst(name)) {
                        try {
                            loadedClass = findClass(name);
                        } catch (ClassNotFoundException ignored) {
                            loadedClass = super.loadClass(name, false);
                        }
                    } else {
                        loadedClass = super.loadClass(name, false);
                    }
                }
                if (resolve) {
                    resolveClass(loadedClass);
                }
                return loadedClass;
            }
        }

        private boolean isChildFirst(String className) {
            return className.startsWith(LOGBACK_PACKAGE)
                    || className.startsWith(ISOLATED_PROVIDER_PACKAGE)
                    || className.startsWith(JANINO_PACKAGE);
        }
    }
}
