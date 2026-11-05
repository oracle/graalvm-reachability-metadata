/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class DefaultGroovyMethodsSupportTest {
    @Test
    void preservesTheComponentTypeWhenRemovingDuplicateArrayEntries() {
        String[] source = {"B", "a", "b", "A"};
        String[] result = DefaultGroovyMethods.toUnique(source, String.CASE_INSENSITIVE_ORDER);
        assertArrayEquals(new String[]{"B", "a"}, result);
        assertEquals(String[].class, result.getClass());
        assertArrayEquals(new String[]{"B", "a", "b", "A"}, source);
    }
}
