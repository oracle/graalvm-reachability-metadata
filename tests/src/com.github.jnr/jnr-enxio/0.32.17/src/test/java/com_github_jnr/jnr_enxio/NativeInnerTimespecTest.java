/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_jnr.jnr_enxio;

import static org.assertj.core.api.Assertions.assertThat;

import jnr.enxio.channels.Native;
import jnr.ffi.Runtime;
import jnr.ffi.Struct;
import org.junit.jupiter.api.Test;

public class NativeInnerTimespecTest {
    @Test
    void representsNativeTimeoutValues() {
        Native.Timespec timeout = new Native.Timespec(Runtime.getSystemRuntime());
        timeout.tv_sec.set(12);
        timeout.tv_nsec.set(345_000_000);

        assertThat(timeout.tv_sec.get()).isEqualTo(12);
        assertThat(timeout.tv_nsec.get()).isEqualTo(345_000_000);
        assertThat(Struct.size(timeout)).isEqualTo(2 * timeout.getRuntime().longSize());

        Native.Timespec mutableTimeout = new Native.Timespec(timeout.getRuntime());
        mutableTimeout.tv_sec.set(27);
        mutableTimeout.tv_nsec.set(890_000_000);

        assertThat(mutableTimeout.tv_sec.get()).isEqualTo(27);
        assertThat(mutableTimeout.tv_nsec.get()).isEqualTo(890_000_000);
    }
}
