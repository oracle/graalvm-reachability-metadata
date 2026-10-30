/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package asm.asm_attrs;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.attrs.EnclosingMethodAttribute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class EnclosingMethodAttributeTest {
    @Test
    void preservesEnclosingMethodOfALocalClass() {
        ClassWriter writer = AttributeTestSupport.newClass();
        writer.visitAttribute(new EnclosingMethodAttribute("example/Factory", "create", "(I)Ljava/lang/Runnable;"));

        EnclosingMethodAttribute result = (EnclosingMethodAttribute) AttributeTestSupport.read(writer).classAttribute("EnclosingMethod");
        assertEquals("example/Factory", result.owner);
        assertEquals("create", result.name);
        assertEquals("(I)Ljava/lang/Runnable;", result.desc);
        assertEquals("owner:example/Factory name:create desc:(I)Ljava/lang/Runnable;", result.toString());
    }

    @Test
    void preservesEnclosingClassWithoutAMethodForAnInitializer() {
        ClassWriter writer = AttributeTestSupport.newClass();
        writer.visitAttribute(new EnclosingMethodAttribute("example/Factory", null, null));

        EnclosingMethodAttribute result = (EnclosingMethodAttribute) AttributeTestSupport.read(writer).classAttribute("EnclosingMethod");
        assertEquals("example/Factory", result.owner);
        assertNull(result.name);
        assertNull(result.desc);
    }
}
