/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring;

import org.junit.jupiter.api.Test;
import org.springframework.util.ObjectUtils;

import static org.junit.jupiter.api.Assertions.*;

public class ObjectUtilsTest {
    @Test
    void preservesComponentTypesWhileAppendingAndBoxing() {
        Object[] appended = ObjectUtils.addObjectToArray(new String[]{"first"}, "second");
        assertInstanceOf(String[].class, appended);
        assertArrayEquals(new String[]{"first", "second"}, appended);
        Object[] boxed = ObjectUtils.toObjectArray(new int[]{5, 8});
        assertInstanceOf(Integer[].class, boxed);
        assertArrayEquals(new Integer[]{5, 8}, boxed);
    }
}
