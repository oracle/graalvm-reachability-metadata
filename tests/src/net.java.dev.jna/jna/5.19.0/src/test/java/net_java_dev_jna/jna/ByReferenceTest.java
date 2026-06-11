/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jna.jna;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ByReferenceTest {
    @Test
    void formatsPointerByReferenceValue() {
        Memory pointer = new Memory(1);
        PointerByReference reference = new PointerByReference(pointer);

        String description = reference.toString();

        assertThat(description)
                .contains("Pointer@0x")
                .contains("=native@0x");
    }

    @Test
    void formatsNullPointerByReferenceValue() {
        PointerByReference reference = new PointerByReference((Pointer) null);

        String description = reference.toString();

        assertThat(description)
                .startsWith("null@0x")
                .doesNotContain("ByReference Contract violated");
    }
}
