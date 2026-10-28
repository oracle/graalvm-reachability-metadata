/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.dubbo;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Time;
import org.junit.jupiter.api.Test;

public class JavaDeserializerInnerSqlTimeFieldDeserializerTest {
    @Test
    void restoresSqlTimeFields() throws Exception {
        assertThat(HessianFieldTestSupport.roundTrip().time).isEqualTo(new Time(2000L));
    }
}
