/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_tngtech_archunit.archunit;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.thirdparty.org.objectweb.asm.ClassReader;
import java.io.IOException;
import org.junit.jupiter.api.Test;

public class ClassReaderTest {
    @Test
    void readsClassBytesFromTheSystemClassLoader() throws IOException {
        ClassReader reader = new ClassReader(AnnotatedFixture.class.getName());

        assertThat(reader.getClassName())
                .isEqualTo("com_tngtech_archunit/archunit/AnnotatedFixture");
        assertThat(reader.getSuperName()).isEqualTo("java/lang/Object");
    }
}
