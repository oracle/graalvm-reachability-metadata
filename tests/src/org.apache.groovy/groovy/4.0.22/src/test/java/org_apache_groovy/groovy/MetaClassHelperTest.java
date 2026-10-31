/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import org.codehaus.groovy.runtime.MetaClassHelper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class MetaClassHelperTest {
    @Test
    void buildsPrimitiveAndCommonComponentArrays() {
        assertArrayEquals(new int[]{4, 9}, (int[]) MetaClassHelper.asPrimitiveArray(List.of(4, 9), int[].class));
        Number[] values = (Number[]) MetaClassHelper.makeCommonArray(new Object[]{"prefix", 4, 2.5}, 1, Number.class);
        assertEquals(Number[].class, values.getClass());
        assertArrayEquals(new Number[]{4, 2.5}, values);
    }
}
