/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import org.codehaus.groovy.runtime.MethodClosure;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MetaClassImplTest {
    @Test
    void createsMultidimensionalArraysThroughAConstructorMethodClosure() {
        MethodClosure constructor = new MethodClosure(String[][].class, "new");
        String[][] matrix = (String[][]) constructor.call(2, 3);
        assertEquals(2, matrix.length);
        assertEquals(3, matrix[0].length);
        matrix[1][2] = "end";
        assertEquals("end", matrix[1][2]);
    }
}
