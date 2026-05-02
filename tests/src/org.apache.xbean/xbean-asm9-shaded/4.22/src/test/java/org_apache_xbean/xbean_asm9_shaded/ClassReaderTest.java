/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_xbean.xbean_asm9_shaded;

import java.io.IOException;

import org.apache.xbean.asm9.ClassReader;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassReaderTest {
    @Test
    void readsClassBytesFromTheSystemClassLoader() throws IOException {
        ClassReader classReader = new ClassReader(ClassReader.class.getName());

        assertThat(classReader.getClassName()).isEqualTo("org/apache/xbean/asm9/ClassReader");
        assertThat(classReader.getSuperName()).isEqualTo("java/lang/Object");
        assertThat(classReader.getInterfaces()).isEmpty();
    }
}
