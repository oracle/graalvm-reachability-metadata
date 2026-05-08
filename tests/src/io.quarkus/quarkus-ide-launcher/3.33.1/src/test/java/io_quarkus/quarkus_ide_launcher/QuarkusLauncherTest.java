/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_quarkus.quarkus_ide_launcher;

import java.io.Closeable;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import io.quarkus.bootstrap.IDELauncherImpl;
import io.quarkus.launcher.QuarkusLauncher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class QuarkusLauncherTest {
    private static final String CALLING_CLASS = "test.app.CallingClass";
    private static final String CLASS_RESOURCE = "test/app/CallingClass.class";

    @TempDir
    Path temporaryDirectory;

    @Test
    void launchLocatesApplicationClassesAndInvokesIdeLauncher() throws Exception {
        Path classFile = temporaryDirectory.resolve(CLASS_RESOURCE);
        Files.createDirectories(classFile.getParent());
        Files.write(classFile, new byte[] {0});

        String originalMainClass = System.getProperty("quarkus.package.main-class");
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        ResourceBackedClassLoader resourceBackedClassLoader = new ResourceBackedClassLoader(originalClassLoader, classFile);
        IDELauncherImpl.reset();

        Closeable closeable = null;
        try {
            Thread.currentThread().setContextClassLoader(resourceBackedClassLoader);

            closeable = QuarkusLauncher.launch(CALLING_CLASS, "test.Application", "first", "second");

            assertThat(closeable).isSameAs(IDELauncherImpl.closeable());
            assertThat(IDELauncherImpl.launchCount()).isEqualTo(1);
            assertThat(IDELauncherImpl.appClasses()).isEqualTo(temporaryDirectory);
            assertThat(IDELauncherImpl.context()).containsEntry("app-classes", temporaryDirectory);
            assertThat((String[]) IDELauncherImpl.context().get("args")).containsExactly("first", "second");
            assertThat(System.getProperty("quarkus.package.main-class")).isEqualTo("test.Application");
            assertThat(Thread.currentThread().getContextClassLoader()).isSameAs(resourceBackedClassLoader);
        } finally {
            if (closeable != null) {
                closeable.close();
            }
            restoreProperty("quarkus.package.main-class", originalMainClass);
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }

        assertThat(IDELauncherImpl.closeableClosed()).isTrue();
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }

    private static final class ResourceBackedClassLoader extends ClassLoader {
        private final Path classFile;

        private ResourceBackedClassLoader(ClassLoader parent, Path classFile) {
            super(parent);
            this.classFile = classFile;
        }

        @Override
        public URL getResource(String name) {
            if (CLASS_RESOURCE.equals(name)) {
                try {
                    return classFile.toUri().toURL();
                } catch (IOException e) {
                    throw new IllegalStateException("Unable to create class resource URL", e);
                }
            }
            return super.getResource(name);
        }
    }
}
