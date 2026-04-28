/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_projectlombok.lombok;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ShadowClassLoaderTest {

    @Test
    void loadsResourcesAndClassesThroughTheShadowLoader() throws Exception {
        ClassLoader shadowLoader = newShadowClassLoader();

        assertThat(shadowLoader.getResource("java/lang/String.class")).isNotNull();
        assertThat(Collections.list(shadowLoader.getResources("java/lang/String.class"))).isNotEmpty();
        assertThat(shadowLoader.loadClass("java.lang.String")).isSameAs(String.class);

        Method addOverrideClasspathEntry = declaredMethod(shadowLoader.getClass(), "addOverrideClasspathEntry", String.class);
        Path overrideDirectory = Files.createTempDirectory("lombok-shadow-override");
        addOverrideClasspathEntry.invoke(shadowLoader, overrideDirectory.toString());

        assertThat(shadowLoader.getResource("lombok/core/Main.SCL.lombok")).isNull();
    }

    @Test
    void fallsBackToTheAlreadyDefinedClassWhenTheSameBytesAreDefinedTwice() throws Exception {
        ClassLoader shadowLoader = newShadowClassLoader();
        Method urlToDefineClass = declaredMethod(shadowLoader.getClass(), "urlToDefineClass", String.class, URL.class, boolean.class);
        URL shadowClass = shadowLoader.getResource("lombok/core/TypeLibrary.SCL.lombok");
        assertThat(shadowClass).isNotNull();

        Class<?> firstDefinition = (Class<?>) urlToDefineClass.invoke(shadowLoader, "lombok.core.TypeLibrary", shadowClass, false);
        Class<?> duplicateDefinition = (Class<?>) urlToDefineClass.invoke(shadowLoader, "lombok.core.TypeLibrary", shadowClass, false);

        assertThat(duplicateDefinition).isSameAs(firstDefinition);
    }

    private static ClassLoader newShadowClassLoader() throws Exception {
        Class<?> shadowClassLoaderClass = Class.forName("lombok.launch.ShadowClassLoader");
        Constructor<?> constructor = shadowClassLoaderClass.getDeclaredConstructor(
                ClassLoader.class,
                String.class,
                String.class,
                List.class,
                List.class
        );
        constructor.setAccessible(true);
        return (ClassLoader) constructor.newInstance(
                ShadowClassLoaderTest.class.getClassLoader(),
                "lombok",
                null,
                List.of(),
                List.of("lombok.patcher.Symbols")
        );
    }

    private static Method declaredMethod(Class<?> type, String name, Class<?>... parameterTypes) throws Exception {
        Method method = type.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method;
    }
}
