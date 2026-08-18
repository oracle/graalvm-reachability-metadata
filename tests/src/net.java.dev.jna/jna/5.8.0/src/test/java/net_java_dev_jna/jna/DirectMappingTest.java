/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jna.jna;

import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises JNA direct mapping, which builds the libffi call descriptors in
 * Java: {@code Native.register} resolves every parameter and return type
 * through {@code Structure$FFIType.get}, running the static initializer that
 * reflectively instantiates {@code Structure$FFIType} and reads its fields.
 */
class DirectMappingTest {

    @Test
    void registerAndCall() {
        Native.register(DirectMappedCLibrary.class, "c");

        assertThat(DirectMappedCLibrary.atol("42")).isEqualTo(42);
        assertThat(DirectMappedCLibrary.labs(new NativeLong(-42)).longValue()).isEqualTo(42L);
    }
}
