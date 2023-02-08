/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_dbcp2;

import org.apache.commons.dbcp2.ListException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

public class TestListException {
    @Test
    public void testExceptionList() {
        final List<Throwable> exceptions = Arrays.asList(new NullPointerException(), new RuntimeException());
        final ListException list = new ListException("Internal Error", exceptions);
        Assertions.assertEquals("Internal Error", list.getMessage());
        Assertions.assertArrayEquals(exceptions.toArray(), list.getExceptionList().toArray());
    }

    @Test
    public void testNulls() {
        final ListException list = new ListException(null, null);
        Assertions.assertNull(list.getMessage());
        Assertions.assertNull(list.getExceptionList());
    }
}
