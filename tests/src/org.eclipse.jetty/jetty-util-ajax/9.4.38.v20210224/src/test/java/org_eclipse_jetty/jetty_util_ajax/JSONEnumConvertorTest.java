/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_util_ajax;

import java.time.DayOfWeek;

import org.eclipse.jetty.util.ajax.JSON;
import org.eclipse.jetty.util.ajax.JSONEnumConvertor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JSONEnumConvertorTest {
    @Test
    void roundTripsEnumDeclaredByClassField() {
        JSON json = new JSON();
        json.addConvertor(DayOfWeek.class, new JSONEnumConvertor(true));

        String serialized = json.toJSON(DayOfWeek.WEDNESDAY);
        Object parsed = json.parse(new JSON.StringSource(serialized));

        assertThat(serialized).contains("\"class\":\"java.time.DayOfWeek\"");
        assertThat(serialized).contains("\"value\":\"WEDNESDAY\"");
        assertThat(parsed).isEqualTo(DayOfWeek.WEDNESDAY);
    }
}
