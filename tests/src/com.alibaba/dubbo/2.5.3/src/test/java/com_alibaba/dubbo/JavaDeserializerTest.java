/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.dubbo;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import org.junit.jupiter.api.Test;

public class JavaDeserializerTest {
    @Test
    void restoresPrimitiveObjectAndSqlFields() throws Exception {
        Fields source = new Fields();
        source.flag = true;
        source.small = 7;
        source.shortNumber = 8;
        source.number = 9;
        source.total = 10L;
        source.ratio = 1.5f;
        source.precise = 2.75d;
        source.text = "restored";
        source.value = Integer.valueOf(11);
        source.date = new Date(1000L);
        source.time = new Time(2000L);
        source.timestamp = new Timestamp(3000L);

        Fields copy = (Fields) HessianTestSupport.roundTrip(source);

        assertThat(copy.flag).isTrue();
        assertThat(copy.small).isEqualTo((byte) 7);
        assertThat(copy.shortNumber).isEqualTo((short) 8);
        assertThat(copy.number).isEqualTo(9);
        assertThat(copy.total).isEqualTo(10L);
        assertThat(copy.ratio).isEqualTo(1.5f);
        assertThat(copy.precise).isEqualTo(2.75d);
        assertThat(copy.text).isEqualTo("restored");
        assertThat(copy.value).isEqualTo(11);
        assertThat(copy.date).isEqualTo(new Date(1000L));
        assertThat(copy.time).isEqualTo(new Time(2000L));
        assertThat(copy.timestamp).isEqualTo(new Timestamp(3000L));
    }

    public static class Fields {
        boolean flag;
        byte small;
        short shortNumber;
        int number;
        long total;
        float ratio;
        double precise;
        String text;
        Object value;
        Date date;
        Time time;
        Timestamp timestamp;
    }
}
