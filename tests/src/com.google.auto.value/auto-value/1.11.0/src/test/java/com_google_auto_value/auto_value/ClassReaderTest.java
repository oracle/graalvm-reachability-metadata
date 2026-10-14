/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_auto_value.auto_value;

import static org.assertj.core.api.Assertions.assertThat;

import autovalue.shaded.org.objectweb.asm.ClassReader;
import org.junit.jupiter.api.Test;

public class ClassReaderTest {
    @Test
    void readsAClassFromTheSystemClassLoader() throws Exception {
        ClassReader reader = new ClassReader("com.google.auto.value.processor.AutoValueProcessor");

        assertThat(reader.getClassName()).isEqualTo("com/google/auto/value/processor/AutoValueProcessor");
        assertThat(reader.getSuperName()).isEqualTo("com/google/auto/value/processor/AutoValueishProcessor");
    }
}
