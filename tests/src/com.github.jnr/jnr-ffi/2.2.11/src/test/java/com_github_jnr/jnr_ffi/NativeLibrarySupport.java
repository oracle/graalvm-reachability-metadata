/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_jnr.jnr_ffi;

import jnr.ffi.LibraryLoader;
import jnr.ffi.Platform;

final class NativeLibrarySupport {
    private NativeLibrarySupport() {
    }

    static <T> LibraryLoader<T> loader(Class<T> type) {
        // The reflection loader binds native functions without defining implementation classes.
        System.setProperty("jnr.ffi.asm.enabled", "false");
        return LibraryLoader.create(type).library(Platform.getNativePlatform().getStandardCLibraryName());
    }

    static <T> T load(Class<T> type) {
        return loader(type).failImmediately().load();
    }
}
