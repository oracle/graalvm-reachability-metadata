/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_modelmapper.modelmapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.modelmapper.internal.bytebuddy.dynamic.ClassFileLocator;
import org.modelmapper.internal.bytebuddy.dynamic.ClassFileLocator.Resolution;

public class ClassFileLocatorInnerForModuleTest {
    private static final int CLASS_FILE_MAGIC = 0xCAFEBABE;

    @Test
    void locatesClassFromBootModuleLayer() throws Exception {
        try (ClassFileLocator locator = ClassFileLocator.ForModule.ofBootLayer()) {
            Resolution resolution = locator.locate(String.class.getName());

            assertThat(resolution.isResolved()).isTrue();
            assertThat(readMagic(resolution.resolve())).isEqualTo(CLASS_FILE_MAGIC);
        }
    }

    private static int readMagic(byte[] classFile) {
        assertThat(classFile).hasSizeGreaterThanOrEqualTo(Integer.BYTES);
        return ((classFile[0] & 0xFF) << 24)
            | ((classFile[1] & 0xFF) << 16)
            | ((classFile[2] & 0xFF) << 8)
            | (classFile[3] & 0xFF);
    }
}
