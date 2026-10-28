/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import net.bytebuddy.jar.asm.ClassReader;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.utility.JavaType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ClassReaderTest {
    @Test
    void readsClassStructureFromLibraryResource() throws Exception {
        ClassReader reader = new ClassReader(JavaType.class.getName());
        assertEquals("net/bytebuddy/utility/JavaType", reader.getClassName());
        assertEquals("java/lang/Enum", reader.getSuperName());
        assertTrue((reader.getAccess() & Opcodes.ACC_ENUM) != 0);
    }
}
