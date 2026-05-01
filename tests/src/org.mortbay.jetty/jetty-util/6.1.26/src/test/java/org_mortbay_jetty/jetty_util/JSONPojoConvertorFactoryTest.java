/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mortbay_jetty.jetty_util;

import org.junit.jupiter.api.Test;
import org.mortbay.util.ajax.JSON;
import org.mortbay.util.ajax.JSONPojoConvertor;
import org.mortbay.util.ajax.JSONPojoConvertorFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class JSONPojoConvertorFactoryTest {
    @Test
    void createsAndCachesPojoConvertorWhenSerializingUnknownPojo() {
        JSON json = new JSON();
        json.addConvertor(Object.class, new JSONPojoConvertorFactory(json));
        FactoryWidget widget = new FactoryWidget("jetty", 61L, true);

        String serialized = json.toJSON(widget);

        assertThat(serialized).contains("\"class\":\"" + FactoryWidget.class.getName() + "\"");
        assertThat(serialized).contains("\"name\":\"jetty\"");
        assertThat(serialized).contains("\"count\":61");
        assertThat(serialized).contains("\"enabled\":true");
        assertThat(json.getConvertorFor(FactoryWidget.class.getName())).isInstanceOf(JSONPojoConvertor.class);
    }

    public static class FactoryWidget {
        private final String name;
        private final long count;
        private final boolean enabled;

        public FactoryWidget(String name, long count, boolean enabled) {
            this.name = name;
            this.count = count;
            this.enabled = enabled;
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
