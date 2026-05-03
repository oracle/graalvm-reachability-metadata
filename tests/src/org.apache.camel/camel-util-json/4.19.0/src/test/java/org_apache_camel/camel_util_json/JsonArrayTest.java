/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_camel.camel_util_json;

import java.time.DayOfWeek;

import org.apache.camel.util.json.JsonArray;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JsonArrayTest {
    @Test
    void getEnumLoadsEnumClassFromStoredQualifiedName() throws Exception {
        JsonArray array = new JsonArray();
        array.add("java.time.DayOfWeek.MONDAY");

        DayOfWeek day = array.getEnum(0);

        assertThat(day).isEqualTo(DayOfWeek.MONDAY);
    }
}
