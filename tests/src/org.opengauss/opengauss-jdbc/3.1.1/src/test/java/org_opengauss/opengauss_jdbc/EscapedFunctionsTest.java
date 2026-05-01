/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_opengauss.opengauss_jdbc;

import org.junit.jupiter.api.Test;
import org.postgresql.jdbc.EscapedFunctions;
import org.postgresql.util.PSQLException;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SuppressWarnings("deprecation")
public class EscapedFunctionsTest {
    @Test
    void functionLookupInitializesDeprecatedEscapeFunctionMap() {
        assertNotNull(EscapedFunctions.getFunction("ceiling"));
        assertNotNull(EscapedFunctions.getFunction("TIMESTAMPADD"));
        assertNull(EscapedFunctions.getFunction("not_a_jdbc_escape_function"));
    }

    @Test
    void translatesCommonJdbcEscapeFunctionsToPostgresSql() throws SQLException {
        assertEquals("ceil(amount)", EscapedFunctions.sqlceiling(List.of("amount")));
        assertEquals("(first_name || ' ' || last_name)",
                EscapedFunctions.sqlconcat(List.of("first_name", "' '", "last_name")));
        assertEquals("substring(name for 4)", EscapedFunctions.sqlleft(List.of("name", "4")));
        assertEquals("substr(description,2,5)", EscapedFunctions.sqlsubstring(List.of("description", "2", "5")));
        assertEquals("current_database()", EscapedFunctions.sqldatabase(Collections.emptyList()));
        assertEquals("(CAST(2 || ' day' as interval)+created_at)",
                EscapedFunctions.sqltimestampadd(List.of("SQL_TSI_DAY", "2", "created_at")));
    }

    @Test
    void rejectsInvalidJdbcEscapeFunctionArityAndIntervals() {
        assertThrows(PSQLException.class, () -> EscapedFunctions.sqlceiling(Collections.emptyList()));
        assertThrows(PSQLException.class, () -> EscapedFunctions.sqlinsert(List.of("value", "1", "2")));
        assertThrows(PSQLException.class,
                () -> EscapedFunctions.sqltimestampadd(List.of("SQL_TSI_FRAC_SECOND", "1", "created_at")));
    }
}
