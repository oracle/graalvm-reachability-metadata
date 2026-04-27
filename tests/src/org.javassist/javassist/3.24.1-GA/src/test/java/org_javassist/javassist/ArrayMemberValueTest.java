/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_javassist.javassist;

import static org.assertj.core.api.Assertions.assertThat;

import javassist.bytecode.ConstPool;
import javassist.bytecode.annotation.Annotation;
import javassist.bytecode.annotation.ArrayMemberValue;
import javassist.bytecode.annotation.MemberValue;
import javassist.bytecode.annotation.StringMemberValue;

import org.junit.jupiter.api.Test;

public class ArrayMemberValueTest {
    @Test
    void createsNestedArrayValuesForProxyInterface() throws Exception {
        ConstPool constPool = new ConstPool(ArrayMemberValueTest.class.getName());
        Annotation annotationMetadata = new Annotation(MatrixView.class.getName(), constPool);
        ArrayMemberValue firstRow = stringArrayValue(constPool, "alpha", "beta");
        ArrayMemberValue secondRow = stringArrayValue(constPool, "gamma", "delta");
        ArrayMemberValue matrixValue = new ArrayMemberValue(firstRow, constPool);
        matrixValue.setValue(new MemberValue[] {firstRow, secondRow});
        annotationMetadata.addMemberValue("matrix", matrixValue);

        ClassLoader classLoader = ArrayMemberValueTest.class.getClassLoader();
        MatrixView matrixView = (MatrixView) annotationMetadata.toAnnotationType(classLoader, null);

        assertThat(matrixView.matrix()).isDeepEqualTo(new String[][] {{"alpha", "beta"}, {"gamma", "delta"}});
    }

    private static ArrayMemberValue stringArrayValue(ConstPool constPool, String... values) {
        MemberValue[] memberValues = new MemberValue[values.length];
        for (int i = 0; i < values.length; i++) {
            memberValues[i] = new StringMemberValue(values[i], constPool);
        }

        ArrayMemberValue arrayMemberValue = new ArrayMemberValue(new StringMemberValue(constPool), constPool);
        arrayMemberValue.setValue(memberValues);
        return arrayMemberValue;
    }

    public interface MatrixView {
        String[][] matrix();
    }
}
