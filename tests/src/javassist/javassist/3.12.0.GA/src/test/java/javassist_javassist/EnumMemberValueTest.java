/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javassist_javassist;

import java.lang.annotation.RetentionPolicy;

import javassist.bytecode.ConstPool;
import javassist.bytecode.annotation.Annotation;
import javassist.bytecode.annotation.EnumMemberValue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class EnumMemberValueTest {
    @Test
    void resolvesEnumConstantWhenAnnotationProxyMemberIsInvoked() throws Exception {
        EnumBackedAnnotation proxy = newAnnotationProxy(RetentionPolicy.RUNTIME);

        assertThat(proxy.value()).isEqualTo(RetentionPolicy.RUNTIME);
    }

    private static EnumBackedAnnotation newAnnotationProxy(RetentionPolicy policy) throws ClassNotFoundException {
        ConstPool constPool = new ConstPool(EnumMemberValueTest.class.getName());
        Annotation annotation = new Annotation(EnumBackedAnnotation.class.getName(), constPool);
        annotation.addMemberValue("value", newEnumMemberValue(policy, constPool));

        ClassLoader classLoader = EnumMemberValueTest.class.getClassLoader();
        return (EnumBackedAnnotation) annotation.toAnnotationType(classLoader, null);
    }

    private static EnumMemberValue newEnumMemberValue(RetentionPolicy policy, ConstPool constPool) {
        EnumMemberValue memberValue = new EnumMemberValue(constPool);
        memberValue.setType(RetentionPolicy.class.getName());
        memberValue.setValue(policy.name());
        return memberValue;
    }

    public @interface EnumBackedAnnotation {
        RetentionPolicy value();
    }
}
