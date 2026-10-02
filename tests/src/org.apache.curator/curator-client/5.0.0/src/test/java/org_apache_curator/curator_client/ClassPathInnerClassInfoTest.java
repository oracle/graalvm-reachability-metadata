/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_curator.curator_client;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.curator.shaded.com.google.common.reflect.ClassPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ClassPathInnerClassInfoTest {
    private static final String CLASS_NAME = ClassPathInnerClassInfoTest.class.getName();
    private static final String CLASS_RESOURCE = CLASS_NAME.replace('.', '/') + ".class";

    @Test
    void loadsAClassDiscoveredByItsAssociatedClassLoader(@TempDir Path classPathRoot) throws Exception {
        Path classResource = classPathRoot.resolve(CLASS_RESOURCE);
        Files.createDirectories(classResource.getParent());
        Files.write(classResource, new byte[0]);

        URL[] classPathUrls = {classPathRoot.toUri().toURL()};
        try (KnownClassLoader classLoader = new KnownClassLoader(classPathUrls)) {
            ClassPath.ClassInfo classInfo = ClassPath.from(classLoader).getAllClasses().stream()
                    .filter(candidate -> candidate.getName().equals(CLASS_NAME))
                    .findFirst()
                    .orElseThrow();

            assertThat(classInfo.getPackageName()).isEqualTo(ClassPathInnerClassInfoTest.class.getPackageName());
            assertThat(classInfo.getSimpleName()).isEqualTo(ClassPathInnerClassInfoTest.class.getSimpleName());
            assertThat(classInfo.load()).isSameAs(ClassPathInnerClassInfoTest.class);
        }
    }

    private static final class KnownClassLoader extends URLClassLoader {
        private KnownClassLoader(URL[] urls) {
            super(urls, null);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (name.equals(CLASS_NAME)) {
                return ClassPathInnerClassInfoTest.class;
            }
            return super.loadClass(name);
        }
    }
}
