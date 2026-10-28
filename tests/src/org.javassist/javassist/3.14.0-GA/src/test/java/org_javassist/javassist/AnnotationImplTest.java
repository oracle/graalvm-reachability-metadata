/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_javassist.javassist;

import javassist.bytecode.ConstPool;
import javassist.bytecode.annotation.Annotation;
import javassist.bytecode.annotation.StringMemberValue;
import org.junit.jupiter.api.Test;
import org_javassist.javassist.TestSupport.Label;
import org_javassist.javassist.TestSupport.LabelValue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AnnotationImplTest {
    @Test
    void convertsBytecodeAnnotationsToTypedValuesWithEqualityAndHashing() throws Exception {
        ConstPool constants = new ConstPool("example.LabelOwner");
        Annotation annotation = new Annotation(Label.class.getName(), constants);
        annotation.addMemberValue("value", new StringMemberValue("invoice", constants));
        Label label = (Label) annotation.toAnnotationType(Label.class.getClassLoader(), null);
        assertEquals("invoice", label.value());
        assertEquals(Label.class, label.annotationType());
        assertTrue(label.equals(new LabelValue("invoice")));
        assertFalse(label.equals(new LabelValue("receipt")));
        assertFalse(label.equals(null));
        int expectedHash = (127 * "value".hashCode()) ^ "invoice".hashCode();
        assertEquals(expectedHash, label.hashCode());
        assertEquals(expectedHash, label.hashCode());
        assertTrue(label.toString().contains("invoice"));
    }
}
