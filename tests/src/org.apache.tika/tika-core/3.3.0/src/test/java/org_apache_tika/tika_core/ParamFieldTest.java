/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tika.tika_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import org.apache.tika.config.Field;
import org.apache.tika.config.ParamField;

public class ParamFieldTest {

    @Test
    public void assignValueSetsAnnotatedField() throws Exception {
        FieldTarget bean = new FieldTarget();
        ParamField paramField = new ParamField(FieldTarget.class.getField("value"));

        paramField.assignValue(bean, "field value");

        assertThat(bean.value).isEqualTo("field value");
        assertThat(paramField.getName()).isEqualTo("value");
        assertThat(paramField.getType()).isEqualTo(String.class);
    }

    @Test
    public void assignValueInvokesAnnotatedSetter() throws Exception {
        SetterTarget bean = new SetterTarget();
        ParamField paramField = new ParamField(
                SetterTarget.class.getMethod("setConfiguredValue", Integer.class));

        paramField.assignValue(bean, 7);

        assertThat(bean.getConfiguredValue()).isEqualTo(7);
        assertThat(paramField.getName()).isEqualTo("configuredValue");
        assertThat(paramField.getType()).isEqualTo(Integer.class);
    }

    public static class FieldTarget {
        @Field
        public String value;
    }

    public static class SetterTarget {
        private Integer configuredValue;

        @Field
        public void setConfiguredValue(Integer configuredValue) {
            this.configuredValue = configuredValue;
        }

        public Integer getConfiguredValue() {
            return configuredValue;
        }
    }
}
