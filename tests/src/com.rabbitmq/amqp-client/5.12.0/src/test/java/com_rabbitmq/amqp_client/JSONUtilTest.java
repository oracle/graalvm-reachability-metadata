/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_rabbitmq.amqp_client;

import com.rabbitmq.tools.json.JSONUtil;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class JSONUtilTest {
    @Test
    void fillPopulatesBeanPropertiesAndPublicFields() throws Exception {
        JsonFillTarget target = new JsonFillTarget();
        Map<String, Object> values = Map.of(
            "propertyValue", "configured-by-setter",
            "publicValue", "configured-by-field"
        );

        Object filled = JSONUtil.fill(target, values);

        assertThat(filled).isSameAs(target);
        assertThat(target.getPropertyValue()).isEqualTo("configured-by-setter");
        assertThat(target.publicValue).isEqualTo("configured-by-field");
    }

    public static class JsonFillTarget {
        public String publicValue;

        private String propertyValue;

        public String getPropertyValue() {
            return propertyValue;
        }

        public void setPropertyValue(String propertyValue) {
            this.propertyValue = propertyValue;
        }
    }
}
