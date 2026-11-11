/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_caucho.hessian;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Timeout(120)
public class SqlDateDeserializerTest {
    @Test
    void restoresSqlTemporalTypes() throws Exception {
        for (Object value : new Object[] {new Date(1700000000000L), new Time(1700000000000L),
                new Timestamp(1700000000000L)}) {
            assertEquals(value, SerializationSupport.roundTrip(value));
        }
    }
}
