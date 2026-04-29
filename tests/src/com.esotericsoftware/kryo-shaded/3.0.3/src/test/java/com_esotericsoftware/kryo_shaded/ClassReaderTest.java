/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_esotericsoftware.kryo_shaded;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.esotericsoftware.reflectasm.shaded.org.objectweb.asm.ClassReader;
import java.io.IOException;
import org.junit.jupiter.api.Test;

public class ClassReaderTest {
    private static final String CLASS_READER_INTERNAL_NAME =
            "com/esotericsoftware/reflectasm/shaded/org/objectweb/asm/ClassReader";

    @Test
    void loadsClassBytesFromSystemClassLoaderResource() throws IOException {
        ClassReader reader = new ClassReader(ClassReader.class.getName());

        assertThat(reader.getClassName()).isEqualTo(CLASS_READER_INTERNAL_NAME);
        assertThat(reader.getSuperName()).isEqualTo("java/lang/Object");
        assertThat(reader.getInterfaces()).isEmpty();
    }

    @Test
    void reportsMissingClassWhenSystemClassLoaderCannotFindResource() {
        assertThatThrownBy(() -> new ClassReader("com_esotericsoftware.kryo_shaded.MissingClassReaderSubject"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Class not found");
    }
}
