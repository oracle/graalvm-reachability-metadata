/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mortbay_jetty.jetty_util;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mortbay.util.ajax.JSON;

import static org.assertj.core.api.Assertions.assertThat;

public class JSONTest {
    @Test
    void restoresConvertibleObjectDeclaredByJsonClassField() {
        JSON json = new JSON();
        RoundTripMetric original = new RoundTripMetric("requests", 12L, true);

        String serialized = json.toJSON(original);
        Object parsed = json.fromJSON(serialized);

        assertThat(serialized).contains("\"class\":\"" + RoundTripMetric.class.getName() + "\"");
        assertThat(parsed).isInstanceOf(RoundTripMetric.class);
        RoundTripMetric restored = (RoundTripMetric) parsed;
        assertThat(restored.name).isEqualTo("requests");
        assertThat(restored.count).isEqualTo(12L);
        assertThat(restored.enabled).isTrue();
    }

    public static class RoundTripMetric implements JSON.Convertible {
        private String name;
        private long count;
        private boolean enabled;

        public RoundTripMetric() {
        }

        RoundTripMetric(String name, long count, boolean enabled) {
            this.name = name;
            this.count = count;
            this.enabled = enabled;
        }

        @Override
        public void toJSON(JSON.Output out) {
            out.addClass(RoundTripMetric.class);
            out.add("name", name);
            out.add("count", count);
            out.add("enabled", enabled);
        }

        @Override
        public void fromJSON(Map object) {
            name = (String) object.get("name");
            count = ((Number) object.get("count")).longValue();
            enabled = ((Boolean) object.get("enabled")).booleanValue();
        }
    }
}
