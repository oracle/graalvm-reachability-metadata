/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_sisu.org_eclipse_sisu_inject;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;

import org.eclipse.sisu.space.asm.ClassReader;
import org.junit.jupiter.api.Test;

public class ClassReaderTest {
    private static final byte[] MINIMAL_CLASS_BYTES = new byte[] {
        (byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE,
        0x00, 0x00, 0x00, 0x34,
        0x00, 0x05,
        0x07, 0x00, 0x02,
        0x01, 0x00, 0x0C,
        'e', 'x', 'a', 'm', 'p', 'l', 'e', '/', 'S', 'i', 's', 'u',
        0x07, 0x00, 0x04,
        0x01, 0x00, 0x10,
        'j', 'a', 'v', 'a', '/', 'l', 'a', 'n', 'g', '/', 'O', 'b', 'j', 'e', 'c', 't',
        0x00, 0x21,
        0x00, 0x01,
        0x00, 0x03,
        0x00, 0x00,
        0x00, 0x00,
        0x00, 0x00,
        0x00, 0x00
    };

    @Test
    void readsClassMetadataFromInputStream() throws Exception {
        ClassReader reader = new ClassReader(new ByteArrayInputStream(MINIMAL_CLASS_BYTES));

        assertThat(reader.getClassName()).isEqualTo("example/Sisu");
        assertThat(reader.getSuperName()).isEqualTo("java/lang/Object");
    }

    @Test
    void readsClassMetadataFromSystemResource() throws Exception {
        ClassReader reader = new ClassReader(ClassReader.class.getName());

        assertThat(reader.getClassName()).isEqualTo("org/eclipse/sisu/space/asm/ClassReader");
        assertThat(reader.getSuperName()).isEqualTo("java/lang/Object");
    }
}
