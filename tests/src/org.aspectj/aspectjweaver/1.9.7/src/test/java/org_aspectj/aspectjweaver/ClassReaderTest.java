/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjweaver;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import aj.org.objectweb.asm.ClassReader;
import java.io.IOException;
import org.junit.jupiter.api.Test;

public class ClassReaderTest {
    @Test
    void reportsMissingClassWhenSystemResourceCannotBeOpened() {
        String missingClassName = ClassReaderTest.class.getName() + "Missing";

        assertThatThrownBy(() -> new ClassReader(missingClassName))
                .isInstanceOf(IOException.class)
                .hasMessage("Class not found");
    }
}
