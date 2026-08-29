/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.asm.ClassReader;

/** Verifies ASM class lookup through the system class loader. */
public class ClassReaderTest {
    @Test
    void readsClassByBinaryName() throws Exception {
        ClassReader reader = new ClassReader("org.springframework.core.SpringVersion");

        assertThat(reader.getClassName()).isEqualTo("org/springframework/core/SpringVersion");
        assertThat(reader.getSuperName()).isEqualTo("java/lang/Object");
    }
}
