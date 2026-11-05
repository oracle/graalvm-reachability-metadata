/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.com.google.common.base.Enums;
import java.lang.reflect.Field;
import java.time.DayOfWeek;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EnumsTest {
    @Test
    void exercisesPublicApi() throws Exception {
        Field field = Enums.getField(DayOfWeek.FRIDAY);
        assertEquals("FRIDAY", field.getName());
        assertEquals(DayOfWeek.class, field.getDeclaringClass());
        assertEquals(DayOfWeek.FRIDAY, Enums.getIfPresent(DayOfWeek.class, field.getName()).get());
    }
}
