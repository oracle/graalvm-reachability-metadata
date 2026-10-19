/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.dubbo;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;

final class HessianFieldTestSupport {
    private HessianFieldTestSupport() { }

    static Fields roundTrip() throws Exception {
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
        return (Fields) HessianTestSupport.roundTrip(source);
    }

    static class Fields {
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
