/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_jnr.jnr_ffi;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectionLibraryLoaderTest {
    public interface StandardC {
        int abs(int value);
    }

    @Test
    void invokesNativeFunctionsThroughTheReflectionLoader() {
        StandardC library = NativeLibrarySupport.load(StandardC.class);

        assertThat(library.abs(-47)).isEqualTo(47);
        assertThat(library.abs(19)).isEqualTo(19);
    }
}
