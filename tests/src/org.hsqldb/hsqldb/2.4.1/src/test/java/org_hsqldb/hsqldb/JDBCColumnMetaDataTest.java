/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import java.sql.Types;

import org.hsqldb.jdbc.JDBCColumnMetaData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class JDBCColumnMetaDataTest {
    @Test
    void describesConfiguredColumnFields() {
        JDBCColumnMetaData column = new JDBCColumnMetaData();
        column.columnName = "TOTAL";
        column.columnLabel = "Order total";
        column.columnType = Types.DECIMAL;
        column.precision = 12;
        column.scale = 2;
        column.isSigned = true;
        String description = column.toString();
        assertTrue(description.contains("columnName=TOTAL"));
        assertTrue(description.contains("columnLabel=Order total"));
        assertTrue(description.contains("precision=12"));
        assertTrue(description.contains("scale=2"));
        assertTrue(description.contains("isSigned=true"));
    }
}
