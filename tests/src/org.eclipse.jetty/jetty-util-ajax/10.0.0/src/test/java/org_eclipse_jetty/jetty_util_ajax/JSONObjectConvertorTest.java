/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_util_ajax;

import java.util.Map;

import org.eclipse.jetty.util.ajax.JSON;
import org.eclipse.jetty.util.ajax.JSONObjectConvertor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JSONObjectConvertorTest {
    @Test
    void serializesPublicBeanGettersAsJsonObjectFields() {
        JSON json = new JSON();
        json.addConvertor(WidgetSnapshot.class, new JSONObjectConvertor(false, new String[] {"secret"}));
        WidgetSnapshot snapshot = new WidgetSnapshot("jetty", 38, true, "hidden");

        Object parsed = JSON.parse(json.toJSON(snapshot));

        assertThat(parsed).isInstanceOf(Map.class);
        Map<?, ?> properties = (Map<?, ?>) parsed;
        assertThat(properties.get("name")).isEqualTo("jetty");
        assertThat(properties.get("count")).isEqualTo(Long.valueOf(38L));
        assertThat(properties.get("ready")).isEqualTo(Boolean.TRUE);
        assertThat(properties.containsKey("secret")).isFalse();
        assertThat(properties.containsKey("description")).isFalse();
    }

    public static class WidgetSnapshot {
        private final String name;
        private final int count;
        private final boolean ready;
        private final String secret;

        public WidgetSnapshot(String name, int count, boolean ready, String secret) {
            this.name = name;
            this.count = count;
            this.ready = ready;
            this.secret = secret;
        }

        public String getName() {
            return name;
        }

        public int getCount() {
            return count;
        }

        public boolean isReady() {
            return ready;
        }

        public String getSecret() {
            return secret;
        }

        public String description() {
            return name + ':' + count;
        }
    }
}
