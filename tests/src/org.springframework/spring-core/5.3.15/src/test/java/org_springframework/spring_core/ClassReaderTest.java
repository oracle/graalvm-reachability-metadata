/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.springframework.asm.ClassReader;

public class ClassReaderTest {

    @Test
    void readsClassFileFromSystemClassLoaderResource() throws IOException {
        ClassReader reader = new ClassReader("org.springframework.core.SpringVersion");

        assertThat(reader.getClassName()).isEqualTo("org/springframework/core/SpringVersion");
    }
}
