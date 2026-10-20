/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kylin.kylin_jdbc;

import org.apache.kylin.jdbc.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BeanPropertyWriterTest {
    @Test
    void serializesPropertiesBackedByAFieldAndAMethod() throws Exception {
        PropertyBean bean = new PropertyBean();
        bean.code = 7;
        bean.setLabel("ready");

        String json = new ObjectMapper().writeValueAsString(bean);

        assertThat(json).contains("\"code\":7", "\"label\":\"ready\"");
    }

    public static class PropertyBean {
        public int code;
        private String label;

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }
    }
}
