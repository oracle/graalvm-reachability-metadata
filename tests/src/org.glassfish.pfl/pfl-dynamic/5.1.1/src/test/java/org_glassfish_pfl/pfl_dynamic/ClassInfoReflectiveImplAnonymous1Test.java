/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_pfl.pfl_dynamic;

import static org.assertj.core.api.Assertions.assertThat;

import org.glassfish.pfl.dynamic.codegen.spi.ClassInfo;
import org.glassfish.pfl.dynamic.codegen.spi.Type;
import org.junit.jupiter.api.Test;

public class ClassInfoReflectiveImplAnonymous1Test {
    @Test
    void describesFieldsMethodsAndConstructors() {
        ClassInfo classInfo = Type.type(DescribedValue.class).classInfo();

        assertThat(classInfo.fieldInfo()).containsKey("number");
        assertThat(classInfo.methodInfoByName()).containsKey("label");
        assertThat(classInfo.constructorInfo()).hasSize(2);
    }

    public static final class DescribedValue {
        private final int number;

        public DescribedValue() {
            this(0);
        }

        public DescribedValue(int number) {
            this.number = number;
        }

        public String label() {
            return "value-" + number;
        }
    }
}
