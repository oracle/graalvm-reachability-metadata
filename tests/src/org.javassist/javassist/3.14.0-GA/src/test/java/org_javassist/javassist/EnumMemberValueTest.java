/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_javassist.javassist;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import javassist.bytecode.ConstPool;
import javassist.bytecode.annotation.Annotation;
import javassist.bytecode.annotation.EnumMemberValue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EnumMemberValueTest {
    @Test
    void resolvesEnumConstantsInTypedAnnotations() throws Exception {
        ConstPool constants = new ConstPool("example.RetentionOwner");
        EnumMemberValue policy = new EnumMemberValue(constants);
        policy.setType(RetentionPolicy.class.getName());
        policy.setValue("RUNTIME");
        Annotation annotation = new Annotation(Retention.class.getName(), constants);
        annotation.addMemberValue("value", policy);
        Retention retention = (Retention) annotation.toAnnotationType(Retention.class.getClassLoader(), null);
        assertEquals(RetentionPolicy.RUNTIME, retention.value());
        policy.setValue("CLASS");
        assertEquals(RetentionPolicy.CLASS, retention.value());
    }
}
