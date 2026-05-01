/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_util_ajax;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.eclipse.jetty.util.ajax.AsyncJSON;
import org.eclipse.jetty.util.ajax.JSON.Convertible;
import org.eclipse.jetty.util.ajax.JSON.Output;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AsyncJSONTest {
    @Test
    void createsConvertibleObjectDeclaredByClassField() {
        AsyncJSON parser = new AsyncJSON.Factory().newAsyncJSON();
        String json = String.format(Locale.ROOT, """
                {
                  "class": "%s",
                  "name": "jetty",
                  "count": 13,
                  "active": true,
                  "tags": ["async", "json"]
                }
                """, ConvertiblePayload.class.getName());

        boolean complete = parser.parse(json.getBytes(StandardCharsets.UTF_8));
        Object parsed = parser.complete();

        assertThat(complete).isTrue();
        assertThat(parsed).isInstanceOf(ConvertiblePayload.class);
        ConvertiblePayload payload = (ConvertiblePayload) parsed;
        assertThat(payload.getName()).isEqualTo("jetty");
        assertThat(payload.getCount()).isEqualTo(13L);
        assertThat(payload.isActive()).isTrue();
        assertThat(payload.getTags()).containsExactly("async", "json");
    }

    public static class ConvertiblePayload implements Convertible {
        private String name;
        private long count;
        private boolean active;
        private List<String> tags;

        public ConvertiblePayload() {
        }

        @Override
        public void toJSON(Output out) {
            out.addClass(ConvertiblePayload.class);
            out.add("name", name);
            out.add("count", count);
            out.add("active", active);
            out.add("tags", tags);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void fromJSON(Map object) {
            name = (String) object.get("name");
            count = ((Number) object.get("count")).longValue();
            active = (Boolean) object.get("active");
            tags = (List<String>) object.get("tags");
        }

        public String getName() {
            return name;
        }

        public long getCount() {
            return count;
        }

        public boolean isActive() {
            return active;
        }

        public List<String> getTags() {
            return tags;
        }
    }
}
