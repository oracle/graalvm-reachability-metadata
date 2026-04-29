/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_modelmapper.modelmapper;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.modelmapper.internal.asm.ClassReader;

public class ClassReaderTest {
    @Test
    void reportsMissingClassWhenLoadingByName() {
        assertThatThrownBy(() -> new ClassReader("org_modelmapper.modelmapper.generated.DoesNotExist"))
            .isInstanceOf(IOException.class)
            .hasMessage("Class not found");
    }
}
