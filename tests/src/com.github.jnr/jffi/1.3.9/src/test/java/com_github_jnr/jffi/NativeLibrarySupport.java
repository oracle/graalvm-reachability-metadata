/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_jnr.jffi;

import com.kenai.jffi.Library;
import com.kenai.jffi.Platform;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class NativeLibrarySupport {
    private NativeLibrarySupport() {
    }

    static Library standardLibrary() {
        Library library = Platform.getPlatform().getOS() == Platform.OS.WINDOWS
                ? Library.getCachedInstance("msvcrt", Library.NOW | Library.LOCAL)
                : Library.getDefault();
        assertNotNull(library, Library.getLastError());
        return library;
    }

    static long symbol(Library library, String name) {
        long address = library.getSymbolAddress(name);
        assertNotEquals(0L, address, () -> name + ": " + Library.getLastError());
        return address;
    }
}
