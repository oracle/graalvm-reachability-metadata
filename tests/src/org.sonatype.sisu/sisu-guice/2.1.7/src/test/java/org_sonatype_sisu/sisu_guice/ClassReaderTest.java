/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_sonatype_sisu.sisu_guice;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.internal.asm.ClassReader;
import java.io.IOException;
import org.junit.jupiter.api.Test;

public class ClassReaderTest {
    @Test
    void readsClassBytesFromTheSystemClassLoader() throws IOException {
        ClassReader reader = new ClassReader(ClassReader.class.getName());

        assertThat(reader.getClassName()).isEqualTo("com/google/inject/internal/asm/ClassReader");
        assertThat(reader.getSuperName()).isEqualTo("java/lang/Object");
        assertThat(reader.getInterfaces()).isEmpty();
    }
}
