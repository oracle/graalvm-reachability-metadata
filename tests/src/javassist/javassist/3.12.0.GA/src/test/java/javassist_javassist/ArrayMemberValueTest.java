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
    void createsProxyWithNestedStringArrayMembers() throws Exception {
        ArrayBackedInterface proxy = newProxy();

        assertThat(proxy.tags()).containsExactly("fast", "native-image");
        Object matrix = proxy.matrix();
        assertThat(matrix).isInstanceOf(String[][].class);
        assertThat((String[][]) matrix).isDeepEqualTo(new String[][] {
                {"alpha", "beta"},
                {"gamma"}
        });
    }

    private static ArrayBackedInterface newProxy() throws ClassNotFoundException {
        ConstPool constPool = new ConstPool(ArrayMemberValueTest.class.getName());
        Annotation annotation = new Annotation(ArrayBackedInterface.class.getName(), constPool);
        annotation.addMemberValue("tags", newStringArray(constPool, "fast", "native-image"));
        annotation.addMemberValue("matrix", newStringMatrix(constPool));

        ClassLoader classLoader = ArrayMemberValueTest.class.getClassLoader();
        return (ArrayBackedInterface) annotation.toAnnotationType(classLoader, null);
    }

    private static ArrayMemberValue newStringArray(ConstPool constPool, String... values) {
        MemberValue[] members = new MemberValue[values.length];
        for (int i = 0; i < values.length; i++) {
            members[i] = new StringMemberValue(values[i], constPool);
        }

        ArrayMemberValue arrayMemberValue = new ArrayMemberValue(constPool);
        arrayMemberValue.setValue(members);
        return arrayMemberValue;
    }

    private static ArrayMemberValue newStringMatrix(ConstPool constPool) {
        ArrayMemberValue matrix = new ArrayMemberValue(constPool);
        matrix.setValue(new MemberValue[] {
                newStringArray(constPool, "alpha", "beta"),
                newStringArray(constPool, "gamma")
        });
        return matrix;
    }

    public interface ArrayBackedInterface {
        String[] tags();

        Object matrix();
    }
}
