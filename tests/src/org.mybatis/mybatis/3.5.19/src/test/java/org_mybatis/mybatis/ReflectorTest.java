/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis;

import java.util.List;

import org.apache.ibatis.reflection.Reflector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ReflectorTest {
    @Test
    void resolvesGenericAndParameterizedArrayProperties() {
        Reflector reflector = new Reflector(ArraysBean.class);
        assertEquals(Object[].class, reflector.getGetterType("values"));
        assertEquals(List[].class, reflector.getGetterType("groups"));
        assertEquals(List[].class, reflector.getSetterType("groups"));
    }

    @Test
    void resolvesParameterizedComponentTypesOfArrayMethods() {
        Reflector reflector = new Reflector(ArrayMethods.class);
        assertEquals(List[].class, reflector.getGetterType("batches"));
        assertEquals(List[].class, reflector.getSetterType("batches"));
    }

    public static class ArrayMethods {
        private List<String>[] batches;

        public List<String>[] getBatches() {
            return batches;
        }

        public void setBatches(List<String>[] batches) {
            this.batches = batches;
        }
    }

    public static class ArraysBean<T> {
        public T[] values;
        public List<String>[] groups;
    }
}
