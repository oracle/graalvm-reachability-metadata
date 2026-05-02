/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_antlr.ST4;

import org.junit.jupiter.api.Test;
import org.stringtemplate.v4.STGroupDir;

import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;

public class STGroupDirTest {
    private static final String CLASSPATH_GROUP_DIR_RESOURCE =
            "org_antlr/ST4/stgroupdir/group-dir-resource.txt";

    @Test
    void resolvesClasspathResourceWithOwningClassLoaderWhenContextClassLoaderMisses() {
        final Thread currentThread = Thread.currentThread();
        final ClassLoader originalContextClassLoader = currentThread.getContextClassLoader();
        final ClassLoader missingResourceClassLoader =
                new MissingResourceClassLoader(originalContextClassLoader);

        try {
            currentThread.setContextClassLoader(missingResourceClassLoader);

            final STGroupDir group = new STGroupDir(CLASSPATH_GROUP_DIR_RESOURCE);
            final URL rootDirUrl = group.getRootDirURL();

            assertThat(rootDirUrl).isNotNull();
            assertThat(rootDirUrl.toExternalForm()).endsWith(CLASSPATH_GROUP_DIR_RESOURCE);
        } finally {
            currentThread.setContextClassLoader(originalContextClassLoader);
        }
    }

    private static final class MissingResourceClassLoader extends ClassLoader {
        private MissingResourceClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public URL getResource(String name) {
            return null;
        }
    }
}
