/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_jnr.jnr_ffi;

import java.util.ArrayList;
import java.util.List;

import jnr.ffi.CallingConvention;
import jnr.ffi.provider.InterfaceScanner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class InterfaceScannerTest {
    public interface Functions {
        int abs(int value);

        default int identity(int value) {
            return value;
        }
    }

    @Test
    void scansNativeMethodsAndExcludesDefaultMethods() {
        InterfaceScanner scanner = new InterfaceScanner(Functions.class, null, CallingConvention.DEFAULT);
        List<String> names = new ArrayList<>();
        scanner.functions().forEach(function -> names.add(function.name()));

        assertThat(names).containsExactly("abs");
    }
}
