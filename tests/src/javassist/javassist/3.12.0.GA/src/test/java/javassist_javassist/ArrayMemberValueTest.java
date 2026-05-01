/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javassist_javassist;

import javassist.bytecode.ConstPool;
import javassist.bytecode.annotation.Annotation;
import javassist.bytecode.annotation.ArrayMemberValue;
import javassist.bytecode.annotation.MemberValue;
import javassist.bytecode.annotation.StringMemberValue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ArrayMemberValueTest {
    @Test
    void toAnnotationTypeMaterializesNestedStringArrays() throws Exception {
        ConstPool constPool = new ConstPool(ArrayMemberValueTest.class.getName());
        ArrayMemberValue rowType = new ArrayMemberValue(new StringMemberValue(constPool), constPool);
        ArrayMemberValue matrixValue = new ArrayMemberValue(rowType, constPool);
        matrixValue.setValue(new MemberValue[] {
                row(constPool, "alpha", "beta"),
                row(constPool, "gamma")
        });
        Annotation annotation = new Annotation(MatrixCarrier.class.getName(), constPool);
        annotation.addMemberValue("value", matrixValue);

        MatrixCarrier carrier = (MatrixCarrier) annotation.toAnnotationType(
                ArrayMemberValueTest.class.getClassLoader(), null);

        String[][] matrix = carrier.value();
        assertThat(matrix.getClass().getComponentType()).isEqualTo(String[].class);
        assertThat(matrix).isDeepEqualTo(new String[][] {
                {"alpha", "beta"},
                {"gamma"}
        });
    }

    private static ArrayMemberValue row(ConstPool constPool, String... values) {
        ArrayMemberValue rowValue = new ArrayMemberValue(new StringMemberValue(constPool), constPool);
        MemberValue[] elements = new MemberValue[values.length];
        for (int i = 0; i < values.length; i++) {
            elements[i] = new StringMemberValue(values[i], constPool);
        }
        rowValue.setValue(elements);
        return rowValue;
    }

    public interface MatrixCarrier {
        String[][] value();
    }
}
