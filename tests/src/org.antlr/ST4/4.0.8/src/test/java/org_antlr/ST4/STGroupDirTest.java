/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_antlr.ST4;

import java.net.URL;

import org.junit.jupiter.api.Test;
import org.stringtemplate.v4.STGroupDir;

import static org.assertj.core.api.Assertions.assertThat;

public class STGroupDirTest {
    private static final String CLASSPATH_GROUP_DIR = "org_antlr/ST4/stgroupdir/classpath-template.st";

    @Test
    void resolvesGroupDirectoryResourceWithThreadContextClassLoader() throws Exception {
        final URL expectedRoot = STGroupDirTest.class.getClassLoader().getResource(CLASSPATH_GROUP_DIR);
        assertThat(expectedRoot).isNotNull();

        final STGroupDir groupDir = withContextClassLoader(
                new SingleResourceClassLoader(CLASSPATH_GROUP_DIR, expectedRoot),
                () -> new STGroupDir(CLASSPATH_GROUP_DIR));

        assertThat(groupDir.getName()).isEqualTo(CLASSPATH_GROUP_DIR);
        assertThat(groupDir.getRootDirURL()).isEqualTo(expectedRoot);
    }

    @Test
    void fallsBackToStGroupDirClassLoaderWhenContextClassLoaderMisses() throws Exception {
        final URL expectedRoot = STGroupDir.class.getClassLoader().getResource(CLASSPATH_GROUP_DIR);
        assertThat(expectedRoot).isNotNull();

        final STGroupDir groupDir = withContextClassLoader(
                new RejectingResourceClassLoader(),
                () -> new STGroupDir(CLASSPATH_GROUP_DIR));

        assertThat(groupDir.getName()).isEqualTo(CLASSPATH_GROUP_DIR);
        assertThat(groupDir.getRootDirURL()).isEqualTo(expectedRoot);
    }

    private static <T> T withContextClassLoader(
            ClassLoader classLoader,
            ThrowingSupplier<T> supplier) throws Exception {
        final Thread thread = Thread.currentThread();
        final ClassLoader previousClassLoader = thread.getContextClassLoader();
        thread.setContextClassLoader(classLoader);
        try {
            return supplier.get();
        } finally {
            thread.setContextClassLoader(previousClassLoader);
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    private static final class SingleResourceClassLoader extends ClassLoader {
        private final String resourceName;
        private final URL resource;

        private SingleResourceClassLoader(String resourceName, URL resource) {
            super(null);
            this.resourceName = resourceName;
            this.resource = resource;
        }

        @Override
        public URL getResource(String name) {
            if (resourceName.equals(name)) {
                return resource;
            }
            return null;
        }
    }

    private static final class RejectingResourceClassLoader extends ClassLoader {
        private RejectingResourceClassLoader() {
            super(null);
        }

        @Override
        public URL getResource(String name) {
            return null;
        }
    }
}
