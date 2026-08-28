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

/** Verifies loading class bytes through ASM's class-name constructor. */
public class ClassReaderTest {
    @Test
    void readsClassFromSystemResource() throws Exception {
        ClassReader reader = new ClassReader(ClassReaderTest.class.getName());

        assertThat(reader.getClassName()).isEqualTo("org_springframework/spring_core/ClassReaderTest");
    }
}
