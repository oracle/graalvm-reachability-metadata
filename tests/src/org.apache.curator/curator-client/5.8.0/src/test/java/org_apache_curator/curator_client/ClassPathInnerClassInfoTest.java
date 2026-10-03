/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_curator.curator_client;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.curator.CuratorZookeeperClient;
import org.apache.curator.shaded.com.google.common.reflect.ClassPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassPathInnerClassInfoTest {
    private static final String CLASS_NAME = CuratorZookeeperClient.class.getName();
    private static final String RESOURCE_NAME = CLASS_NAME.replace('.', '/') + ".class";

    @TempDir
    Path tempDirectory;

    @Test
    void loadsAClassDiscoveredByClasspathScanning() throws Exception {
        Path classFile = tempDirectory.resolve(RESOURCE_NAME);
        Files.createDirectories(classFile.getParent());
        Files.write(classFile, new byte[0]);

        URL[] urls = {tempDirectory.toUri().toURL()};
        try (ClassLoaderReturningCuratorClass classLoader = new ClassLoaderReturningCuratorClass(urls)) {
            ClassPath.ClassInfo classInfo = ClassPath.from(classLoader).getAllClasses().stream()
                    .filter(info -> CLASS_NAME.equals(info.getName()))
                    .findFirst()
                    .orElseThrow();

            assertThat(classInfo.getPackageName()).isEqualTo(CuratorZookeeperClient.class.getPackageName());
            assertThat(classInfo.getSimpleName()).isEqualTo(CuratorZookeeperClient.class.getSimpleName());
            assertThat(classInfo.isTopLevel()).isTrue();
            assertThat(classInfo.load()).isSameAs(CuratorZookeeperClient.class);
        }
    }

    private static final class ClassLoaderReturningCuratorClass extends URLClassLoader {
        private ClassLoaderReturningCuratorClass(URL[] urls) {
            super(urls, null);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (CLASS_NAME.equals(name)) {
                return CuratorZookeeperClient.class;
            }
            return super.loadClass(name);
        }
    }
}
