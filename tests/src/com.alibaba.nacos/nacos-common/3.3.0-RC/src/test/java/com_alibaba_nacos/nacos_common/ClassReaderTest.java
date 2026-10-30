/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba_nacos.nacos_common;

import com.alibaba.nacos.common.packagescan.classreading.ClassReader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ClassReaderTest {
    @Test
    void readsClassStructureFromAPackagedLibraryClass() throws Exception {
        ClassReader reader = new ClassReader("com.alibaba.nacos.common.pathencoder.impl.WindowsEncoder");
        assertEquals("com/alibaba/nacos/common/pathencoder/impl/WindowsEncoder", reader.getClassName());
        assertEquals("java/lang/Object", reader.getSuperName());
        assertArrayEquals(new String[]{"com/alibaba/nacos/common/pathencoder/PathEncoder"}, reader.getInterfaces());
        assertTrue(reader.getItemCount() > 0);
    }
}
