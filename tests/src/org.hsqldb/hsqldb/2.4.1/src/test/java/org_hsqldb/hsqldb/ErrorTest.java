/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import org.hsqldb.HsqlException;
import org.hsqldb.error.Error;
import org.hsqldb.error.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ErrorTest {
    @Test
    void resolvesSqlStatesToErrorCodesAndMessages() {
        int code = Error.getCode("23505");
        assertEquals(ErrorCode.X_23505, code);
        HsqlException exception = Error.error(code);
        assertEquals("23505", exception.getSQLState());
        assertTrue(exception.getMessage().contains("unique"));
        assertEquals(ErrorCode.X_08003, Error.getCode("08003"));
        assertEquals(-1, Error.getCode("ZZZZZ"));
    }
}
