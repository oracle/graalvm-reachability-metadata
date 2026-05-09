/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_webapp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.util.Collections;
import java.util.Enumeration;

import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.webapp.WebAppClassLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class WebAppClassLoaderTest {
    @Test
    void loadsClassFromParentWhenParentLoaderHasPriority() throws Exception {
        TestContext context = new TestContext(true, false, false);

        try (WebAppClassLoader loader = new WebAppClassLoader(applicationClassLoader(), context)) {
            Class<?> loadedClass = loader.loadClass("java.lang.String");

            assertThat(loadedClass).isSameAs(String.class);
        }
    }

    @Test
    void loadsClassFromParentAfterWebAppLookupWhenParentLoaderDoesNotHavePriority() throws Exception {
        TestContext context = new TestContext(false, false, false);

        try (WebAppClassLoader loader = new WebAppClassLoader(applicationClassLoader(), context)) {
            Class<?> loadedClass = loader.loadClass("java.lang.String");

            assertThat(loadedClass).isSameAs(String.class);
        }
    }

    @Test
    void readsResourceEnumerationFromParent(@TempDir Path temporaryDirectory) throws Exception {
        String resourceName = "parent-enumeration.txt";
        TestContext context = new TestContext(false, false, false);

        try (URLClassLoader parent = parentClassLoaderWithResource(temporaryDirectory, resourceName);
                WebAppClassLoader loader = new WebAppClassLoader(parent, context)) {
            Enumeration<URL> resources = loader.getResources(resourceName);

            assertThat(Collections.list(resources)).hasSize(1);
        }
    }

    @Test
    void resolvesParentResourceBeforeWebAppResourceWhenParentLoaderHasPriority(@TempDir Path temporaryDirectory)
            throws Exception {
        String resourceName = "parent-first.txt";
        TestContext context = new TestContext(true, false, false);

        try (URLClassLoader parent = parentClassLoaderWithResource(temporaryDirectory, resourceName);
                WebAppClassLoader loader = new WebAppClassLoader(parent, context)) {
            URL resource = loader.getResource(resourceName);

            assertThat(resource).isNotNull();
            assertThat(resource.getPath()).endsWith(resourceName);
        }
    }

    @Test
    void fallsBackToParentResourceAfterWebAppLookup(@TempDir Path temporaryDirectory) throws Exception {
        String resourceName = "parent-fallback.txt";
        TestContext context = new TestContext(false, false, false);

        try (URLClassLoader parent = parentClassLoaderWithResource(temporaryDirectory, resourceName);
                WebAppClassLoader loader = new WebAppClassLoader(parent, context)) {
            URL resource = loader.getResource(resourceName);

            assertThat(resource).isNotNull();
            assertThat(resource.getPath()).endsWith(resourceName);
        }
    }

    private static ClassLoader applicationClassLoader() {
        ClassLoader classLoader = WebAppClassLoaderTest.class.getClassLoader();
        if (classLoader != null) {
            return classLoader;
        }
        return ClassLoader.getSystemClassLoader();
    }

    private static URLClassLoader parentClassLoaderWithResource(Path temporaryDirectory, String resourceName)
            throws IOException {
        Path resourceFile = temporaryDirectory.resolve(resourceName);
        Files.createDirectories(resourceFile.getParent());
        Files.writeString(resourceFile, "parent resource");
        return new URLClassLoader(new URL[] { temporaryDirectory.toUri().toURL() }, null);
    }

    private static final class TestContext implements WebAppClassLoader.Context {
        private final boolean parentLoaderPriority;
        private final boolean systemClass;
        private final boolean serverClass;
        private final PermissionCollection permissions = new Permissions();

        private TestContext(boolean parentLoaderPriority, boolean systemClass, boolean serverClass) {
            this.parentLoaderPriority = parentLoaderPriority;
            this.systemClass = systemClass;
            this.serverClass = serverClass;
        }

        @Override
        public Resource newResource(String urlOrPath) throws IOException {
            return Resource.newResource(urlOrPath);
        }

        @Override
        public PermissionCollection getPermissions() {
            return permissions;
        }

        @Override
        public boolean isSystemClass(String clazz) {
            return systemClass;
        }

        @Override
        public boolean isServerClass(String clazz) {
            return serverClass;
        }

        @Override
        public boolean isParentLoaderPriority() {
            return parentLoaderPriority;
        }

        @Override
        public String getExtraClasspath() {
            return null;
        }
    }
}
