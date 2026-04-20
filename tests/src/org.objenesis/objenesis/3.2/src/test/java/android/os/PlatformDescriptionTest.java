/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package android.os;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

public class PlatformDescriptionTest {

    private static final String PLATFORM_DESCRIPTION_CLASS = "org.objenesis.strategy.PlatformDescription";
    private static final String DALVIK_VM_NAME = "Dalvik";

    @Test
    void describesLegacyAndroidApiLevelsUsingTheSdkFallbackField() throws Exception {
        Assumptions.assumeFalse(isNativeImageRuntime());

        String originalVmName = System.getProperty("java.vm.name");

        System.setProperty("java.vm.name", DALVIK_VM_NAME);
        try (ChildFirstUrlClassLoader classLoader = new ChildFirstUrlClassLoader(
            runtimeClasspathUrls(),
            PlatformDescriptionTest.class.getClassLoader())) {
            Class<?> platformDescriptionClass =
                Class.forName(PLATFORM_DESCRIPTION_CLASS, true, classLoader);
            Method describePlatform = platformDescriptionClass.getMethod("describePlatform");
            Method isAndroidOpenJdk = platformDescriptionClass.getMethod("isAndroidOpenJDK");

            String description = (String) describePlatform.invoke(null);
            boolean androidOpenJdk = (Boolean) isAndroidOpenJdk.invoke(null);

            Assertions.assertThat(description).contains("API level=3");
            Assertions.assertThat(description).contains("JVM name=\"Dalvik\"");
            Assertions.assertThat(androidOpenJdk).isFalse();
        } finally {
            restoreSystemProperty("java.vm.name", originalVmName);
        }
    }

    private static boolean isNativeImageRuntime() {
        return "runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"));
    }

    private static URL[] runtimeClasspathUrls() {
        String classPath = System.getProperty("java.class.path", "");
        String[] entries = classPath.split(Pattern.quote(File.pathSeparator));
        List<URL> urls = new ArrayList<>();

        for (String entry : entries) {
            if (entry.isBlank()) {
                continue;
            }
            try {
                urls.add(Path.of(entry).toUri().toURL());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
        return urls.toArray(URL[]::new);
    }

    private static void restoreSystemProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
            return;
        }
        System.setProperty(key, value);
    }

    static final class ChildFirstUrlClassLoader extends URLClassLoader {

        ChildFirstUrlClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass == null && usesChildFirstLoading(name)) {
                    try {
                        loadedClass = findClass(name);
                    } catch (ClassNotFoundException ignored) {
                        loadedClass = super.loadClass(name, false);
                    }
                } else if (loadedClass == null) {
                    loadedClass = super.loadClass(name, false);
                }
                if (resolve) {
                    resolveClass(loadedClass);
                }
                return loadedClass;
            }
        }

        private boolean usesChildFirstLoading(String className) {
            return PLATFORM_DESCRIPTION_CLASS.equals(className) || className.startsWith("android.os.");
        }
    }
}

final class Build {

    private Build() {
    }

    public static final class VERSION {
        public static String SDK = "3";

        private VERSION() {
        }
    }
}
