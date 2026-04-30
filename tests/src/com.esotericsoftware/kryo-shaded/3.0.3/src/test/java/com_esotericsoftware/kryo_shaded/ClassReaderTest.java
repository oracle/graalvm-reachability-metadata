/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_esotericsoftware.kryo_shaded;

import com.esotericsoftware.reflectasm.shaded.org.objectweb.asm.ClassReader;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassReaderTest {
    @Test
    void readsClassBytesFromSystemResource() throws Exception {
        ClassReader reader = new ClassReader(ClassReaderTest.class.getName());

        assertThat(reader.getClassName()).isEqualTo("com_esotericsoftware/kryo_shaded/ClassReaderTest");
        assertThat(reader.getSuperName()).isEqualTo("java/lang/Object");
    }
}
