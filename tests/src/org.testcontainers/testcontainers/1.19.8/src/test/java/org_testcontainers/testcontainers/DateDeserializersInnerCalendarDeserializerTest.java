/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

import java.util.GregorianCalendar;

import static org.assertj.core.api.Assertions.assertThat;

public class DateDeserializersInnerCalendarDeserializerTest {
    @Test
    void instantiatesAndPopulatesAGregorianCalendar() throws Exception {
        GregorianCalendar calendar = new ObjectMapper().readValue("0", GregorianCalendar.class);

        assertThat(calendar.getTimeInMillis()).isZero();
    }
}
