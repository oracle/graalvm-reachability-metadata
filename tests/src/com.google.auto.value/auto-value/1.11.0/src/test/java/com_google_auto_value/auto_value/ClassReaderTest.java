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
        ClassReader reader = new ClassReader("java.lang.String");

        assertThat(reader.getClassName()).isEqualTo("java/lang/String");
        assertThat(reader.getSuperName()).isEqualTo("java/lang/Object");
    }
}
