/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.javassist.bytecode.ConstPool;
import org.apache.pulsar.shade.javassist.bytecode.annotation.Annotation;
import org.apache.pulsar.shade.javassist.bytecode.annotation.ArrayMemberValue;
import org.apache.pulsar.shade.javassist.bytecode.annotation.MemberValue;
import org.apache.pulsar.shade.javassist.bytecode.annotation.StringMemberValue;
import org.junit.jupiter.api.Test;

public class ArrayMemberValueTest {
    @Test
    void convertsNestedMemberValuesToTypedArrays() throws Exception {
        ConstPool constPool = new ConstPool(getClass().getName());
        ArrayMemberValue first = stringArray(constPool, "persistent", "non-persistent");
        ArrayMemberValue second = stringArray(constPool, "partitioned");
        ArrayMemberValue matrix = new ArrayMemberValue(first, constPool);
        matrix.setValue(new MemberValue[] {first, second});
        Annotation annotation = new Annotation(ArrayCarrier.class.getName(), constPool);
        annotation.addMemberValue("values", matrix);

        ArrayCarrier proxy = (ArrayCarrier) annotation.toAnnotationType(getClass().getClassLoader(), null);

        assertThat(proxy.values()).containsExactly(
                new String[] {"persistent", "non-persistent"},
                new String[] {"partitioned"});
    }

    private static ArrayMemberValue stringArray(ConstPool constPool, String... values) {
        MemberValue[] members = new MemberValue[values.length];
        for (int i = 0; i < values.length; i++) {
            members[i] = new StringMemberValue(values[i], constPool);
        }
        ArrayMemberValue result = new ArrayMemberValue(constPool);
        result.setValue(members);
        return result;
    }

    public @interface ArrayCarrier {
        String[][] values();
    }
}
