/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_jnr.jnr_ffi;

import jnr.ffi.Runtime;
import jnr.ffi.TypeAlias;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class NativeRuntimeTest {
    @Test
    void resolvesPlatformTypeAliases() {
        Runtime runtime = Runtime.getSystemRuntime();

        assertThat(runtime.findType(TypeAlias.size_t).size()).isEqualTo(runtime.addressSize());
        assertThat(runtime.findType(TypeAlias.int32_t).size()).isEqualTo(4);
    }
}
