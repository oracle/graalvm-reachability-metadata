/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_jnr.jnr_ffi;

import jnr.ffi.LibraryLoader;
import jnr.ffi.Platform;

import static org.assertj.core.api.Assertions.assertThat;

public final class JnrFfiFixtures {
    private JnrFfiFixtures() {
    }

    public interface StandardC {
        int abs(int value);
    }

    public static StandardC loadStandardC() {
        return LibraryLoader.create(StandardC.class)
                .load(Platform.getNativePlatform().getStandardCLibraryName());
    }

    public static void assertNativeInvocation() {
        assertThat(loadStandardC().abs(-37)).isEqualTo(37);
    }
}
