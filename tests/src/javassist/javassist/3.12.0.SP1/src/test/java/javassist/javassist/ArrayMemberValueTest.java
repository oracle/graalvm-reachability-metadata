/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javassist.javassist;

import static org.assertj.core.api.Assertions.assertThat;

import javassist.bytecode.ConstPool;
import javassist.bytecode.annotation.Annotation;
import javassist.bytecode.annotation.ArrayMemberValue;
import javassist.bytecode.annotation.MemberValue;
import javassist.bytecode.annotation.StringMemberValue;

import org.junit.jupiter.api.Test;

public class ArrayMemberValueTest {
    @Test
    void convertsArrayMemberValueToSingleDimensionArrayThroughProxy() throws Exception {
        ConstPool constPool = new ConstPool(SingleDimensionArrayCarrier.class.getName());
        ArrayMemberValue memberValue = newStringArrayMemberValue(constPool, "alpha", "beta");
        Annotation annotation = newAnnotation(SingleDimensionArrayCarrier.class, constPool, memberValue);

        SingleDimensionArrayCarrier proxy = (SingleDimensionArrayCarrier) annotation.toAnnotationType(
                ArrayMemberValueTest.class.getClassLoader(), null);

        assertThat(proxy.values()).containsExactly("alpha", "beta");
    }

    @Test
    void convertsNestedArrayMemberValueToNestedArrayThroughProxy() throws Exception {
        ConstPool constPool = new ConstPool(NestedArrayCarrier.class.getName());
        ArrayMemberValue firstRow = newStringArrayMemberValue(constPool, "alpha", "beta");
        ArrayMemberValue secondRow = newStringArrayMemberValue(constPool, "gamma");
        ArrayMemberValue memberValue = new ArrayMemberValue(firstRow, constPool);
        memberValue.setValue(new MemberValue[] {firstRow, secondRow });
        Annotation annotation = newAnnotation(NestedArrayCarrier.class, constPool, memberValue);

        NestedArrayCarrier proxy = (NestedArrayCarrier) annotation.toAnnotationType(
                ArrayMemberValueTest.class.getClassLoader(), null);
        String[][] values = proxy.values();

        assertThat(values.length).isEqualTo(2);
        assertThat(values[0]).containsExactly("alpha", "beta");
        assertThat(values[1]).containsExactly("gamma");
    }

    private static Annotation newAnnotation(Class<?> carrierType, ConstPool constPool, MemberValue values) {
        Annotation annotation = new Annotation(carrierType.getName(), constPool);
        annotation.addMemberValue("values", values);
        return annotation;
    }

    private static ArrayMemberValue newStringArrayMemberValue(ConstPool constPool, String... values) {
        MemberValue[] memberValues = new MemberValue[values.length];
        for (int i = 0; i < values.length; i++) {
            memberValues[i] = new StringMemberValue(values[i], constPool);
        }

        ArrayMemberValue memberValue = new ArrayMemberValue(constPool);
        memberValue.setValue(memberValues);
        return memberValue;
    }

    public interface SingleDimensionArrayCarrier {
        String[] values();
    }

    public interface NestedArrayCarrier {
        String[][] values();
    }
}
