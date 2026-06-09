/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_jackson.jackson_mapper_asl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.GregorianCalendar;

import org.codehaus.jackson.map.ObjectMapper;
import org.junit.jupiter.api.Test;

public class StdDeserializerInnerCalendarDeserializerTest {
    @Test
    public void deserializesGregorianCalendarUsingConcreteCalendarType() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        long timestamp = 1_234_567_890L;

        GregorianCalendar calendar = mapper.readValue(Long.toString(timestamp), GregorianCalendar.class);

        assertThat(calendar).isExactlyInstanceOf(GregorianCalendar.class);
        assertThat(calendar.getTimeInMillis()).isEqualTo(timestamp);
    }
}
