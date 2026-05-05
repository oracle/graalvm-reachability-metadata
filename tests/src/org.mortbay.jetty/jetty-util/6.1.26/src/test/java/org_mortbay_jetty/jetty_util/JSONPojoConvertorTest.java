/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mortbay_jetty.jetty_util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mortbay.util.ajax.JSON;
import org.mortbay.util.ajax.JSONPojoConvertor;

public class JSONPojoConvertorTest {
    @Test
    void fromJSONCreatesPojoWithDefaultConstructorAndPopulatesProperties() {
        final JSONPojoConvertor convertor = new JSONPojoConvertor(SamplePojo.class, false);
        final Map properties = new LinkedHashMap();
        properties.put("name", "Ada");
        properties.put("count", Long.valueOf(42L));
        properties.put("active", Boolean.TRUE);

        final SamplePojo pojo = (SamplePojo) convertor.fromJSON(properties);

        assertThat(pojo.getName()).isEqualTo("Ada");
        assertThat(pojo.getCount()).isEqualTo(42);
        assertThat(pojo.isActive()).isTrue();
    }

    @Test
    void toJSONInvokesDiscoveredGetters() {
        final JSON json = new JSON();
        json.addConvertor(SamplePojo.class, new JSONPojoConvertor(SamplePojo.class, false));
        final SamplePojo pojo = new SamplePojo("Grace", 7, true);

        final String serialized = json.toJSON(pojo);
        final Map parsed = (Map) json.fromJSON(serialized);

        assertThat(parsed).containsEntry("name", pojo.getName());
        assertThat(parsed).containsEntry("count", Long.valueOf(pojo.getCount()));
        assertThat(parsed).containsEntry("active", Boolean.TRUE);
    }

    public static class SamplePojo {
        private String name;
        private int count;
        private boolean active;

        public SamplePojo() {
        }

        public SamplePojo(final String name, final int count, final boolean active) {
            this.name = name;
            this.count = count;
            this.active = active;
        }

        public String getName() {
            return name;
        }

        public void setName(final String name) {
            this.name = name;
        }

        public int getCount() {
            return count;
        }

        public void setCount(final int count) {
            this.count = count;
        }

        public boolean isActive() {
            return active;
        }

        public void setActive(final boolean active) {
            this.active = active;
        }
    }
}
