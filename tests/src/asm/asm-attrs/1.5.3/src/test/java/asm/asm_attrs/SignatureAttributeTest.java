/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package asm.asm_attrs;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Constants;
import org.objectweb.asm.attrs.SignatureAttribute;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SignatureAttributeTest {
    @Test
    void preservesGenericClassFieldAndMethodSignatures() {
        String classSignature = "<T:Ljava/lang/Object;>Ljava/lang/Object;";
        String fieldSignature = "Ljava/util/List<TT;>;";
        String methodSignature = "()TT;";
        ClassWriter writer = AttributeTestSupport.newClass();
        writer.visitAttribute(new SignatureAttribute(classSignature));
        writer.visitField(Constants.ACC_PUBLIC, "items", "Ljava/util/List;", null, new SignatureAttribute(fieldSignature));
        writer.visitMethod(Constants.ACC_PUBLIC | Constants.ACC_ABSTRACT, "first", "()Ljava/lang/Object;", null,
                new SignatureAttribute(methodSignature));

        AttributeTestSupport result = AttributeTestSupport.read(writer);
        SignatureAttribute classAttribute = (SignatureAttribute) result.classAttribute("Signature");
        SignatureAttribute fieldAttribute = (SignatureAttribute) result.fieldAttribute("items", "Signature");
        SignatureAttribute methodAttribute = (SignatureAttribute) result.methodAttribute("first", "Signature");
        assertEquals(classSignature, classAttribute.signature);
        assertEquals(fieldSignature, fieldAttribute.signature);
        assertEquals(methodSignature, methodAttribute.signature);
        assertEquals(classSignature, classAttribute.toString());
    }
}
