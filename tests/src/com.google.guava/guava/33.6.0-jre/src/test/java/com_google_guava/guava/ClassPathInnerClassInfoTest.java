/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_guava.guava;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.reflect.ClassPath;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ClassPathInnerClassInfoTest {
    private static final String FIXTURE_PACKAGE = "com_google_guava.guava.classpathfixture";
    private static final String FIXTURE_CLASS_NAME = FIXTURE_PACKAGE + ".LoadedFixture";
    private static final String FIXTURE_RESOURCE_PATH =
            "com_google_guava/guava/classpathfixture/LoadedFixture.class";
    private static final String FIXTURE_CLASS_BASE64 = """
            yv66vgAAAD0ADQoAAgADBwAEDAAFAAYBABBqYXZhL2xhbmcvT2JqZWN0AQAGPGluaXQ+AQADKClW
            BwAIAQA1Y29tX2dvb2dsZV9ndWF2YS9ndWF2YS9jbGFzc3BhdGhmaXh0dXJlL0xvYWRlZEZpeHR1
            cmUBAARDb2RlAQAPTGluZU51bWJlclRhYmxlAQAKU291cmNlRmlsZQEAEkxvYWRlZEZpeHR1cmUu
            amF2YQAxAAcAAgAAAAAAAQABAAUABgABAAkAAAAdAAEAAQAAAAUqtwABsQAAAAEACgAAAAYAAQAA
            AAMAAQALAAAAAgAM
            """;

    @Test
    void loadLoadsClassDiscoveredFromClassPath(@TempDir Path classPathRoot) throws Exception {
        writeFixtureClass(classPathRoot);

        URL[] urls = {classPathRoot.toUri().toURL()};
        try (URLClassLoader loader = new URLClassLoader(urls, null)) {
            ClassPath.ClassInfo classInfo = findFixtureClassInfo(loader);

            assertThat(classInfo.getName()).isEqualTo(FIXTURE_CLASS_NAME);
            assertThat(classInfo.getPackageName()).isEqualTo(FIXTURE_PACKAGE);
            assertThat(classInfo.getSimpleName()).isEqualTo("LoadedFixture");
            assertThat(classInfo.isTopLevel()).isTrue();

            try {
                Class<?> loadedClass = classInfo.load();

                assertThat(loadedClass.getName()).isEqualTo(FIXTURE_CLASS_NAME);
                assertThat(loadedClass.getClassLoader()).isSameAs(loader);
            } catch (RuntimeException exception) {
                if (!hasExpectedNativeImageClassLoadingFailure(exception)) {
                    throw exception;
                }
            } catch (Error error) {
                if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                    throw error;
                }
            }
        }
    }

    private static void writeFixtureClass(Path classPathRoot) throws Exception {
        Path classFile = classPathRoot.resolve(FIXTURE_RESOURCE_PATH);
        Files.createDirectories(classFile.getParent());
        Files.write(classFile, Base64.getMimeDecoder().decode(FIXTURE_CLASS_BASE64));
    }

    private static ClassPath.ClassInfo findFixtureClassInfo(ClassLoader loader) throws Exception {
        return ClassPath.from(loader).getAllClasses().stream()
                .filter(classInfo -> classInfo.getName().equals(FIXTURE_CLASS_NAME))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Could not find " + FIXTURE_CLASS_NAME));
    }

    private static boolean hasExpectedNativeImageClassLoadingFailure(Throwable throwable) {
        if (!"runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"))) {
            return false;
        }

        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ClassNotFoundException && FIXTURE_CLASS_NAME.equals(current.getMessage())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
