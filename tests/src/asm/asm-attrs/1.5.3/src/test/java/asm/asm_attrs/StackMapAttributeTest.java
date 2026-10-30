/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package asm.asm_attrs;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.CodeVisitor;
import org.objectweb.asm.Constants;
import org.objectweb.asm.Label;
import org.objectweb.asm.attrs.StackMapAttribute;
import org.objectweb.asm.attrs.StackMapFrame;
import org.objectweb.asm.attrs.StackMapType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StackMapAttributeTest {
    @Test
    void preservesBranchFrameLocalAndOperandStackTypes() {
        ClassWriter writer = AttributeTestSupport.newClass();
        CodeVisitor method = writer.visitMethod(Constants.ACC_PUBLIC | Constants.ACC_STATIC, "identity",
                "(Ljava/lang/Object;)Ljava/lang/Object;", null, null);
        Label nonNull = new Label();
        method.visitVarInsn(Constants.ALOAD, 0);
        method.visitInsn(Constants.DUP);
        method.visitJumpInsn(Constants.IFNONNULL, nonNull);
        method.visitInsn(Constants.POP);
        method.visitInsn(Constants.ACONST_NULL);
        method.visitInsn(Constants.ARETURN);
        method.visitLabel(nonNull);
        method.visitInsn(Constants.ARETURN);

        StackMapType objectType = StackMapType.getTypeInfo(StackMapType.ITEM_Object);
        objectType.setObject("java/lang/Object");
        StackMapFrame frame = new StackMapFrame();
        frame.label = nonNull;
        frame.locals.add(objectType);
        frame.stack.add(objectType);
        StackMapAttribute stackMap = new StackMapAttribute();
        stackMap.frames.add(frame);
        method.visitAttribute(stackMap);
        method.visitMaxs(2, 1);

        StackMapAttribute result = (StackMapAttribute) AttributeTestSupport.read(writer).codeAttribute("identity", "StackMap");
        assertEquals(1, result.frames.size());
        StackMapFrame resultFrame = (StackMapFrame) result.frames.get(0);
        assertSame(resultFrame, result.getFrame(resultFrame.label));
        assertNull(result.getFrame(new Label()));
        assertEquals(1, resultFrame.locals.size());
        assertEquals(1, resultFrame.stack.size());
        StackMapType local = (StackMapType) resultFrame.locals.get(0);
        StackMapType operand = (StackMapType) resultFrame.stack.get(0);
        assertEquals(StackMapType.ITEM_Object, local.getType());
        assertEquals("java/lang/Object", local.getObject());
        assertEquals(StackMapType.ITEM_Object, operand.getType());
        assertEquals("java/lang/Object", operand.getObject());
        assertTrue(result.toString().contains("Object:java/lang/Object"));
    }
}
