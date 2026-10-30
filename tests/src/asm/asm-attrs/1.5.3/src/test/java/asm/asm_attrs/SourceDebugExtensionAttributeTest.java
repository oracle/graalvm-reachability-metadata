/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package asm.asm_attrs;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.attrs.SourceDebugExtensionAttribute;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SourceDebugExtensionAttributeTest {
    @Test
    void preservesSourceMapForGeneratedSource() {
        String sourceMap = """
                SMAP
                Document.java
                Template
                *S Template
                *F
                + 1 document.tpl
                templates/document.tpl
                *L
                1#1,5:10
                *E
                """;
        ClassWriter writer = AttributeTestSupport.newClass();
        writer.visitAttribute(new SourceDebugExtensionAttribute(sourceMap));

        SourceDebugExtensionAttribute result = (SourceDebugExtensionAttribute) AttributeTestSupport.read(writer)
                .classAttribute("SourceDebugExtension");
        assertEquals(sourceMap, result.debugExtension);
        assertEquals(sourceMap, result.toString());
    }
}
