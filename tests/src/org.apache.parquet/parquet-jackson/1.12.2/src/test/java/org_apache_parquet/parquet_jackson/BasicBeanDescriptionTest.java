/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_parquet.parquet_jackson;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import shaded.parquet.com.fasterxml.jackson.databind.ObjectMapper;
import shaded.parquet.com.fasterxml.jackson.databind.introspect.BasicBeanDescription;

public class BasicBeanDescriptionTest {
    @Test
    void instantiatesBeanUsingDefaultConstructor() {
        BasicBeanDescription description = beanDescription(DefaultConstructibleBean.class);

        Object value = description.instantiateBean(false);

        assertThat(value).isInstanceOf(DefaultConstructibleBean.class);
        assertThat(((DefaultConstructibleBean) value).message()).isEqualTo("constructed");
    }

    private static BasicBeanDescription beanDescription(Class<?> beanType) {
        ObjectMapper mapper = new ObjectMapper();
        return (BasicBeanDescription) mapper.getDeserializationConfig().introspect(mapper.constructType(beanType));
    }

    public static final class DefaultConstructibleBean {
        private final String message;

        public DefaultConstructibleBean() {
            this.message = "constructed";
        }

        public String message() {
            return message;
        }
    }
}
