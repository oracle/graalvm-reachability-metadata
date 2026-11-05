/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RoutineTest {
    @Test
    void invokesJavaFunctionsAndInOutProcedures() throws Exception {
        try (Connection connection = DatabaseSupport.memory(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE FUNCTION magnitude(p_input INTEGER) RETURNS INTEGER
                    LANGUAGE JAVA DETERMINISTIC NO SQL
                    EXTERNAL NAME 'CLASSPATH:java.lang.Math.abs'
                    """);
            try (ResultSet result = statement.executeQuery("VALUES magnitude(-19)")) {
                assertTrue(result.next());
                assertEquals(19, result.getInt(1));
            }
            statement.execute("""
                    CREATE PROCEDURE increase(INOUT p_input INTEGER)
                    LANGUAGE JAVA NO SQL
                    EXTERNAL NAME 'CLASSPATH:org_hsqldb.hsqldb.RoutineTest.increase'
                    """);
            try (CallableStatement call = connection.prepareCall("{call increase(?)}")) {
                call.setInt(1, 7);
                call.registerOutParameter(1, Types.INTEGER);
                call.execute();
                assertEquals(12, call.getInt(1));
            }
        }
    }

    public static void increase(Integer[] value) {
        value[0] += 5;
    }
}
