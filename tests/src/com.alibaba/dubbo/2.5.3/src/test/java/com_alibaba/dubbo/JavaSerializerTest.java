/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.dubbo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;
import org.junit.jupiter.api.Test;

public class JavaSerializerTest {
    @Test
    void roundTripsFieldsOfEverySupportedKind() throws Exception {
        Fields source = new Fields();
        source.flag = true;
        source.number = 42;
        source.total = 123456789L;
        source.ratio = 1.25f;
        source.precise = 2.5d;
        source.text = "hessian";
        source.created = new Date(1234L);

        Fields copy = (Fields) HessianTestSupport.roundTrip(source);

        assertThat(copy.flag).isTrue();
        assertThat(copy.number).isEqualTo(42);
        assertThat(copy.total).isEqualTo(123456789L);
        assertThat(copy.ratio).isEqualTo(1.25f);
        assertThat(copy.precise).isEqualTo(2.5d);
        assertThat(copy.text).isEqualTo("hessian");
        assertThat(copy.created).isEqualTo(new Date(1234L));
    }

    public static class Fields {
        boolean flag;
        int number;
        long total;
        float ratio;
        double precise;
        String text;
        Date created;
    }
}
