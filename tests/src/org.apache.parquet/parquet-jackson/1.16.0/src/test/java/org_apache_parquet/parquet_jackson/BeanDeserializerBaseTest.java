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

public class BeanDeserializerBaseTest {
    @Test
    void deserializesNonStaticInnerClassPropertyWithEnclosingBeanConstructor() throws Exception {
        final ObjectMapper mapper = new ObjectMapper();

        final EnclosingBean bean = mapper.readValue("""
                {
                  "inner" : {
                    "value" : "created by enclosing bean"
                  }
                }
                """, EnclosingBean.class);

        assertThat(bean.inner).isNotNull();
        assertThat(bean.inner.value).isEqualTo("created by enclosing bean");
        assertThat(bean.inner.enclosing()).isSameAs(bean);
    }

    public static class EnclosingBean {
        public InnerBean inner;

        public class InnerBean {
            public String value;

            EnclosingBean enclosing() {
                return EnclosingBean.this;
            }
        }
    }
}
