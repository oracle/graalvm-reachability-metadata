/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_pfl.pfl_dynamic;

import static org.assertj.core.api.Assertions.assertThat;

import org.glassfish.pfl.dynamic.copyobject.spi.LibraryClassLoader;
import org.junit.jupiter.api.Test;

public class LibraryClassLoaderTest {
    @Test
    void loadsAnApplicationClassThroughTheLibraryLoader() throws ClassNotFoundException {
        Class<?> loaded = LibraryClassLoader.loadClass(LoadableValue.class.getName());

        assertThat(loaded).isEqualTo(LoadableValue.class);
        assertThat(loaded.getClassLoader()).isEqualTo(LibraryClassLoader.getClassLoader());
    }

    public static final class LoadableValue {}
}
