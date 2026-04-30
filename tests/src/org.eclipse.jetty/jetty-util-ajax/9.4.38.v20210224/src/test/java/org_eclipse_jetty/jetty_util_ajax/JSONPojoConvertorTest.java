/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_util_ajax;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jetty.util.ajax.JSON;
import org.eclipse.jetty.util.ajax.JSONPojoConvertor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JSONPojoConvertorTest {
    @Test
    void createsPojoWithDefaultConstructorAndSettersFromJsonProperties() {
        JSONPojoConvertor convertor = new JSONPojoConvertor(RoundTripWidget.class);
        Map<String, Object> properties = new HashMap<>();
        properties.put("name", "jetty");
        properties.put("count", Long.valueOf(7L));
        properties.put("enabled", Boolean.TRUE);

        Object converted = convertor.fromJSON(properties);

        assertThat(converted).isInstanceOf(RoundTripWidget.class);
        RoundTripWidget widget = (RoundTripWidget) converted;
        assertThat(widget.getName()).isEqualTo("jetty");
        assertThat(widget.getCount()).isEqualTo(7);
        assertThat(widget.isEnabled()).isTrue();
    }

    @Test
    void serializesPojoByInvokingPublicGetters() {
        JSON json = new JSON();
        json.addConvertor(RoundTripWidget.class, new JSONPojoConvertor(RoundTripWidget.class));
        RoundTripWidget widget = new RoundTripWidget("ajax", 11, true);

        Object parsed = JSON.parse(json.toJSON(widget));

        assertThat(parsed).isInstanceOf(Map.class);
        Map<?, ?> properties = (Map<?, ?>) parsed;
        assertThat(properties.get("class")).isEqualTo(RoundTripWidget.class.getName());
        assertThat(properties.get("name")).isEqualTo("ajax");
        assertThat(properties.get("count")).isEqualTo(Long.valueOf(11L));
        assertThat(properties.get("enabled")).isEqualTo(Boolean.TRUE);
    }

    public static class RoundTripWidget {
        private String name;
        private int count;
        private boolean enabled;

        public RoundTripWidget() {
        }

        public RoundTripWidget(String name, int count, boolean enabled) {
            this.name = name;
            this.count = count;
            this.enabled = enabled;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
