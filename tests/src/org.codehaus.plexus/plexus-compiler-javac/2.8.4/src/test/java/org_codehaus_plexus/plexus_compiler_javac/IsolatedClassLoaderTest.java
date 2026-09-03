/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_compiler_javac;

import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

import org.codehaus.plexus.compiler.javac.IsolatedClassLoader;

public class IsolatedClassLoaderTest {

    @Test
    void delegatesToParentClassLoaderWhenClassIsNotInIsolatedUrls() throws Exception {
        try (final IsolatedClassLoader classLoader = new IsolatedClassLoader()) {
            final Class<?> loadedClass = classLoader.loadClass(String.class.getName());

            assertSame(String.class, loadedClass);
        }
    }
}
