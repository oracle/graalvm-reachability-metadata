/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_esotericsoftware.kryo_shaded;

import static org.assertj.core.api.Assertions.assertThatIOException;

import com.esotericsoftware.reflectasm.shaded.org.objectweb.asm.ClassReader;
import org.junit.jupiter.api.Test;

public class ClassReaderTest {
    @Test
    void classNameConstructorReportsMissingClassResource() {
        assertThatIOException()
                .isThrownBy(() -> new ClassReader("com_esotericsoftware.kryo_shaded.NoSuchClassReaderInput"))
                .withMessage("Class not found");
    }
}
