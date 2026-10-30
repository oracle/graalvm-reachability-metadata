/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_jnr.jnr_ffi;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class InterfaceScannerTest {
    public interface StandardC {
        int abs(int value);

        default int identity(int value) {
            return value;
        }
    }

    @Test
    void eagerlyBindsNativeMethodsWithoutBindingJavaDefaultMethods() {
        StandardC library = NativeLibrarySupport.load(StandardC.class);

        assertThat(library.abs(-53)).isEqualTo(53);
    }
}
