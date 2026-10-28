/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis;

import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaClass;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TypeParameterResolverTest {
    @Test
    void resolvesInheritedGenericArrayGetterAndSetter() {
        MetaClass meta = MetaClass.forClass(Names.class, new DefaultReflectorFactory());
        assertEquals(String[].class, meta.getGetterType("values"));
        assertEquals(String[].class, meta.getSetterType("values"));
    }

    public static class Values<T> {
        private T[] values;

        public T[] getValues() {
            return values;
        }

        public void setValues(T[] values) {
            this.values = values;
        }
    }

    public static class Names extends Values<String> {
    }
}
