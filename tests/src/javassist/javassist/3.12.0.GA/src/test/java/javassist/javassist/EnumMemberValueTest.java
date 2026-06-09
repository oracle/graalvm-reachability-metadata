/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javassist.javassist;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import javassist.ClassClassPath;
import javassist.ClassPool;
import javassist.bytecode.ConstPool;
import javassist.bytecode.annotation.Annotation;
import javassist.bytecode.annotation.EnumMemberValue;

import org.junit.jupiter.api.Test;

public class EnumMemberValueTest {
    @Test
    void convertsEnumMemberValueToRuntimeAnnotationConstant()
            throws Exception {
        Annotation annotation = newEnumAnnotation();
        ClassPool classPool = new ClassPool(true);
        classPool.insertClassPath(new ClassClassPath(EnumMemberValueTest.class));

        RuntimeEnumAnnotation proxy = (RuntimeEnumAnnotation) annotation.toAnnotationType(
                EnumMemberValueTest.class.getClassLoader(), classPool);

        assertThat(proxy.value()).isSameAs(FixtureStatus.ENABLED);
        assertThat(proxy.annotationType()).isSameAs(RuntimeEnumAnnotation.class);
    }

    private static Annotation newEnumAnnotation() {
        ConstPool constPool = new ConstPool(EnumMemberValueTest.class.getName());
        Annotation annotation = new Annotation(RuntimeEnumAnnotation.class.getName(), constPool);
        EnumMemberValue value = new EnumMemberValue(constPool);
        value.setType(FixtureStatus.class.getName());
        value.setValue(FixtureStatus.ENABLED.name());
        annotation.addMemberValue("value", value);
        return annotation;
    }

    public enum FixtureStatus {
        ENABLED,
        DISABLED
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface RuntimeEnumAnnotation {
        FixtureStatus value();
    }
}
