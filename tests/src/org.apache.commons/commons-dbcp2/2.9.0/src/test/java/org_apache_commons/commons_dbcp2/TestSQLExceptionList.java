/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_dbcp2;

import org.apache.commons.dbcp2.SQLExceptionList;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.sql.SQLTransientException;
import java.util.Collections;
import java.util.List;

public class TestSQLExceptionList {
    @Test
    public void testCause() {
        final SQLTransientException cause = new SQLTransientException();
        final List<SQLTransientException> list = Collections.singletonList(cause);
        final SQLExceptionList sqlExceptionList = new SQLExceptionList(list);
        Assertions.assertEquals(cause, sqlExceptionList.getCause());
        Assertions.assertEquals(list, sqlExceptionList.getCauseList());
        sqlExceptionList.printStackTrace();
    }

    @Test
    public void testNullCause() {
        final SQLExceptionList sqlExceptionList = new SQLExceptionList(null);
        Assertions.assertNull(sqlExceptionList.getCause());
        Assertions.assertNull(sqlExceptionList.getCauseList());
    }
}
