/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_caucho.hessian;

import com.caucho.hessian.io.SerializerFactory;
import org.junit.jupiter.api.Test;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SqlDateDeserializerTest {
    @Test
    void preservesSqlTemporalTypesAndMilliseconds() throws Exception {
        long millis = 1700000000123L;
        Object[] values = {new Date(millis), new Time(millis), new Timestamp(millis)};
        for (boolean hessian2 : new boolean[] {false, true}) {
            for (Object original : values) {
                Object restored = HessianStreams.roundTrip(original, new SerializerFactory(), hessian2);
                assertEquals(original.getClass(), restored.getClass());
                assertEquals(original, restored);
            }
        }
    }
}
