/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_compiler_javac;

import org.codehaus.plexus.compiler.javac.IsolatedClassLoader;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class IsolatedClassLoaderTest {
    @Test
    void delegatesToParentClassLoaderWhenClassIsNotFoundInIsolatedLoader() throws Exception {
        try (IsolatedClassLoader classLoader = new IsolatedClassLoader()) {
            Class<?> loadedClass = classLoader.loadClass(String.class.getName());

            assertThat(loadedClass).isSameAs(String.class);
        }
    }
}
