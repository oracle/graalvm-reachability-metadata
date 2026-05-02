/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package android.os;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.springframework.objenesis.strategy.PlatformDescription;

public class PlatformDescriptionTest {

    private static final String DALVIK_VM_NAME = "Dalvik";
    private static final String TARGET_CLASS_NAME =
            "org.springframework.objenesis.strategy.PlatformDescription";
    static final String PROBE_CLASS_NAME = "android.os.PlatformDescriptionProbe";
    static final String DESCRIPTION_PROPERTY =
            "org_springframework.spring_core.PlatformDescriptionTest.description";
    static final String ANDROID_OPENJDK_PROPERTY =
            "org_springframework.spring_core.PlatformDescriptionTest.androidOpenJdk";

    @Test
    void describesLegacyAndroidApiLevelsUsingTheSdkFallbackField() throws Exception {
        String originalVmName = System.getProperty("java.vm.name");
        String originalDescription = System.getProperty(DESCRIPTION_PROPERTY);
        String originalAndroidOpenJdk = System.getProperty(ANDROID_OPENJDK_PROPERTY);

        System.setProperty("java.vm.name", DALVIK_VM_NAME);
        System.clearProperty(DESCRIPTION_PROPERTY);
        System.clearProperty(ANDROID_OPENJDK_PROPERTY);
        try (ChildFirstUrlClassLoader classLoader = new ChildFirstUrlClassLoader(
                runtimeClasspathUrls(),
                PlatformDescriptionTest.class.getClassLoader())) {

            Class.forName(PROBE_CLASS_NAME, true, classLoader);

            assertThat(System.getProperty(DESCRIPTION_PROPERTY))
                    .contains("JVM name=\"Dalvik\"")
                    .contains("API level=7");
            assertThat(System.getProperty(ANDROID_OPENJDK_PROPERTY)).isEqualTo("false");
        } catch (Error error) {
            ignoreUnsupportedDynamicClassLoading(error);
        } finally {
            restoreSystemProperty("java.vm.name", originalVmName);
            restoreSystemProperty(DESCRIPTION_PROPERTY, originalDescription);
            restoreSystemProperty(ANDROID_OPENJDK_PROPERTY, originalAndroidOpenJdk);
        }
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
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
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

    private static void ignoreUnsupportedDynamicClassLoading(Error error) {
        if (NativeImageSupport.isUnsupportedFeatureError(error)) {
            return;
        }
        Throwable cause = error.getCause();
        if (cause instanceof Error && NativeImageSupport.isUnsupportedFeatureError((Error) cause)) {
            return;
        }
        throw error;
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
                    loadedClass = loadChildFirstClass(name);
                }
                if (loadedClass == null) {
                    loadedClass = super.loadClass(name, false);
                }
                if (resolve) {
                    resolveClass(loadedClass);
                }
                return loadedClass;
            }
        }

        private Class<?> loadChildFirstClass(String name) throws ClassNotFoundException {
            try {
                return findClass(name);
            } catch (ClassNotFoundException exception) {
                return super.loadClass(name, false);
            }
        }

        private boolean usesChildFirstLoading(String className) {
            return className.equals(PROBE_CLASS_NAME)
                    || className.equals(TARGET_CLASS_NAME)
                    || className.startsWith("android.os.");
        }
    }
}

final class PlatformDescriptionProbe {

    static {
        System.setProperty(
                PlatformDescriptionTest.DESCRIPTION_PROPERTY,
                PlatformDescription.describePlatform()
        );
        System.setProperty(
                PlatformDescriptionTest.ANDROID_OPENJDK_PROPERTY,
                Boolean.toString(PlatformDescription.isAndroidOpenJDK())
        );
    }

    private PlatformDescriptionProbe() {
    }
}

final class Build {

    private Build() {
    }

    public static final class VERSION {
        public static String SDK = "7";

        private VERSION() {
        }
    }
}
