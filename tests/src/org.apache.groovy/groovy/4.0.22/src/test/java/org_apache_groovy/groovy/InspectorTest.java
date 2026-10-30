/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import groovy.inspect.Inspector;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class InspectorTest {
    @Test
    void describesPublicMembersAndTheirValues() {
        Inspector inspector = new Inspector(new Entry());
        assertTrue(Arrays.stream(inspector.getMethods()).map(String[].class::cast)
                .anyMatch(info -> "label".equals(info[Inspector.MEMBER_NAME_IDX])));
        assertEquals(inspector.getMethods().length, inspector.getMethodsWithInfo().length);
        String[] field = Arrays.stream(inspector.getPublicFields()).map(String[].class::cast)
                .filter(info -> "count".equals(info[Inspector.MEMBER_NAME_IDX])).findFirst().orElseThrow();
        assertEquals("7", field[Inspector.MEMBER_VALUE_IDX]);
    }

    public static class Entry {
        public int count = 7;

        public String label() {
            return "entry";
        }
    }
}
