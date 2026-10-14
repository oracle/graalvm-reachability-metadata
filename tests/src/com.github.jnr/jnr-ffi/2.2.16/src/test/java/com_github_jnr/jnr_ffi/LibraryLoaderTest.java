/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_jnr.jnr_ffi;

import jnr.ffi.LibraryLoader;
import jnr.ffi.LibraryOption;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class LibraryLoaderTest {
    public interface MissingLibrary {
        int value();
    }

    @Test
    void delaysALibraryLoadFailureUntilProxyInvocation() {
        MissingLibrary library = LibraryLoader.create(MissingLibrary.class)
                .option(LibraryOption.LoadNow, true)
                .load("jnr_ffi_intentionally_missing_library");

        assertThatThrownBy(library::value).isInstanceOf(LinkageError.class);
    }
}
