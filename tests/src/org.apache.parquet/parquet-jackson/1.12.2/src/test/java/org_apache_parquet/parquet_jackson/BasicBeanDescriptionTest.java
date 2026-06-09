/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_parquet.parquet_jackson;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import shaded.parquet.com.fasterxml.jackson.databind.BeanDescription;
import shaded.parquet.com.fasterxml.jackson.databind.ObjectMapper;

public class BasicBeanDescriptionTest {
    @Test
    void instantiateBeanCreatesDefaultConstructibleBean() {
        final ObjectMapper mapper = new ObjectMapper();
        final BeanDescription description = mapper.getDeserializationConfig()
                .introspect(mapper.constructType(DefaultConstructibleBean.class));

        final Object bean = description.instantiateBean(true);

        assertThat(bean).isInstanceOf(DefaultConstructibleBean.class);
        assertThat(((DefaultConstructibleBean) bean).value).isEqualTo("created by constructor");
    }

    public static final class DefaultConstructibleBean {
        private final String value;

        public DefaultConstructibleBean() {
            value = "created by constructor";
        }
    }
}
