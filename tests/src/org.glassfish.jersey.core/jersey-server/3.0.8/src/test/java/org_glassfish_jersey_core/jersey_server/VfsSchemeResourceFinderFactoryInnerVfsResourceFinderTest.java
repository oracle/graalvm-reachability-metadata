/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jersey_core.jersey_server;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import jakarta.ws.rs.core.Application;

import org.glassfish.jersey.server.ResourceFinder;
import org.glassfish.jersey.server.internal.scanning.PackageNamesScanner;
import org.jboss.vfs.TempFileProvider;
import org.jboss.vfs.VFS;
import org.jboss.vfs.VirtualFile;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class VfsSchemeResourceFinderFactoryInnerVfsResourceFinderTest {
    @Test
    void scansAndOpensResourcesFromAVirtualFileSystem() throws Exception {
        URL apiArchive = Application.class.getProtectionDomain().getCodeSource().getLocation();
        VirtualFile mountDirectory = VFS.getChild("jersey-vfs-test");
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

        try (TempFileProvider provider = TempFileProvider.create("jersey-vfs-test", executor, false);
             Closeable mount = VFS.mountZip(VFS.getChild(apiArchive), mountDirectory, provider)) {
            URL packageUrl = mountDirectory.getChild("jakarta/ws/rs/core").toURL();
            PackageNamesScanner.setResourcesProvider(new SingleResourceProvider(packageUrl));

            try (ResourceFinder finder = new PackageNamesScanner(
                    getClass().getClassLoader(), new String[]{"jakarta.ws.rs.core"}, true)) {
                boolean applicationClassFound = false;
                while (finder.hasNext()) {
                    String resourceName = finder.next();
                    if ("Application.class".equals(resourceName)) {
                        try (InputStream stream = finder.open()) {
                            assertThat(stream.read()).isNotEqualTo(-1);
                        }
                        applicationClassFound = true;
                        break;
                    }
                }

                assertThat(applicationClassFound).isTrue();
            }
        } finally {
            PackageNamesScanner.setResourcesProvider(new ContextClassLoaderResourcesProvider());
            executor.shutdownNow();
        }
    }

    private static final class SingleResourceProvider extends PackageNamesScanner.ResourcesProvider {
        private final URL resource;

        private SingleResourceProvider(URL resource) {
            this.resource = resource;
        }

        @Override
        public Enumeration<URL> getResources(String name, ClassLoader classLoader) {
            return Collections.enumeration(Collections.singleton(resource));
        }
    }

    private static final class ContextClassLoaderResourcesProvider extends PackageNamesScanner.ResourcesProvider {
        @Override
        public Enumeration<URL> getResources(String name, ClassLoader classLoader) throws IOException {
            return classLoader.getResources(name);
        }
    }
}
