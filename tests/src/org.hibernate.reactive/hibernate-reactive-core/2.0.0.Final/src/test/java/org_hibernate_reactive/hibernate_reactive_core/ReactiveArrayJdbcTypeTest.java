/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_reactive.hibernate_reactive_core;

import org.hibernate.reactive.type.descriptor.jdbc.ReactiveArrayJdbcType;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.descriptor.jdbc.IntegerJdbcType;
import org.hibernate.type.descriptor.jdbc.VarcharJdbcType;
import org.hibernate.type.spi.TypeConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReactiveArrayJdbcTypeTest {
    @Test
    void resolvesArrayDescriptorsThatRoundTripElementValues() {
        TypeConfiguration types = new TypeConfiguration();
        ReactiveArrayJdbcType integers = new ReactiveArrayJdbcType(IntegerJdbcType.INSTANCE);
        JavaType<Integer[]> integerArray = integers.getJdbcRecommendedJavaTypeMapping(null, null, types);
        Integer[] identifiers = {17, 29};
        assertEquals(Integer[].class, integerArray.getJavaTypeClass());
        assertArrayEquals(identifiers, integerArray.fromString(integerArray.toString(identifiers)));
        assertTrue(integerArray.areEqual(identifiers, new Integer[]{17, 29}));

        ReactiveArrayJdbcType strings = new ReactiveArrayJdbcType(VarcharJdbcType.INSTANCE);
        JavaType<String[]> stringArray = strings.getJdbcRecommendedJavaTypeMapping(100, null, types);
        String[] labels = {"one", "two"};
        assertEquals(String[].class, stringArray.getJavaTypeClass());
        assertArrayEquals(labels, stringArray.fromString(stringArray.toString(labels)));
    }
}
