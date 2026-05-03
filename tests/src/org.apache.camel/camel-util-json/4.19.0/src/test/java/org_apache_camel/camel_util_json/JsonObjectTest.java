/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_camel.camel_util_json;

import java.time.DayOfWeek;
import java.time.temporal.ChronoUnit;

import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JsonObjectTest {
    @Test
    void getEnumLoadsEnumClassFromStoredQualifiedName() throws Exception {
        JsonObject object = new JsonObject();
        object.put("day", "java.time.DayOfWeek.MONDAY");

        DayOfWeek day = object.getEnum("day");

        assertThat(day).isEqualTo(DayOfWeek.MONDAY);
    }

    @Test
    void getEnumOrDefaultLoadsEnumClassFromStoredQualifiedName() throws Exception {
        JsonObject object = new JsonObject();
        object.put("unit", "java.time.temporal.ChronoUnit.DAYS");

        ChronoUnit unit = object.getEnumOrDefault("unit", ChronoUnit.HOURS);

        assertThat(unit).isEqualTo(ChronoUnit.DAYS);
    }
}
