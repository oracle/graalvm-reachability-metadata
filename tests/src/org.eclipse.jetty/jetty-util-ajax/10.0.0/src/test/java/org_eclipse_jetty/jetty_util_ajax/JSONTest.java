/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_util_ajax;

import java.util.Locale;
import java.util.Map;

import org.eclipse.jetty.util.ajax.JSON;
import org.eclipse.jetty.util.ajax.JSON.Convertible;
import org.eclipse.jetty.util.ajax.JSON.Output;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JSONTest {
    @Test
    void createsConvertibleObjectDeclaredByClassField() {
        JSON json = new JSON();
        String source = String.format(Locale.ROOT, """
                {
                  "class": "%s",
                  "name": "jetty",
                  "count": 21,
                  "enabled": true
                }
                """, ConvertiblePayload.class.getName());

        Object parsed = json.parse(new JSON.StringSource(source));

        assertThat(parsed).isInstanceOf(ConvertiblePayload.class);
        ConvertiblePayload payload = (ConvertiblePayload) parsed;
        assertThat(payload.getName()).isEqualTo("jetty");
        assertThat(payload.getCount()).isEqualTo(21L);
        assertThat(payload.isEnabled()).isTrue();
    }

    public static class ConvertiblePayload implements Convertible {
        private String name;
        private long count;
        private boolean enabled;

        public ConvertiblePayload() {
        }

        @Override
        public void toJSON(Output out) {
            out.addClass(ConvertiblePayload.class);
            out.add("name", name);
            out.add("count", count);
            out.add("enabled", enabled);
        }

        @Override
        public void fromJSON(Map object) {
            name = (String) object.get("name");
            count = ((Number) object.get("count")).longValue();
            enabled = (Boolean) object.get("enabled");
        }

        public String getName() {
            return name;
        }

        public long getCount() {
            return count;
        }

        public boolean isEnabled() {
            return enabled;
        }
    }
}
