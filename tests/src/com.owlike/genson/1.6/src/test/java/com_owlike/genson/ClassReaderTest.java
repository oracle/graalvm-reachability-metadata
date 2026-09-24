/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_owlike.genson;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.owlike.genson.internal.asm.ClassReader;
import java.io.IOException;
import org.junit.jupiter.api.Test;

public class ClassReaderTest {
    @Test
    void rejectsMissingClassResource() {
        assertThatThrownBy(() -> new ClassReader("example.missing.GensonClass"))
                .isInstanceOf(IOException.class)
                .hasMessage("Class not found");
    }
}
