/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_jnr.jnr_ffi;

import jnr.ffi.NativeType;
import jnr.ffi.Runtime;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FFIProviderInnerSystemProviderSingletonHolderTest {
    @Test
    void loadsTheSystemProviderAndItsRuntime() {
        Runtime runtime = Runtime.getSystemRuntime();

        assertThat(runtime.findType(NativeType.SINT)).isNotNull();
        assertThat(runtime.addressSize()).isIn(4, 8);
    }
}
