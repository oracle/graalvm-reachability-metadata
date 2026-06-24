/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_jackson.jackson_mapper_asl;

import static org.assertj.core.api.Assertions.assertThat;

import org.codehaus.jackson.map.ObjectMapper;
import org.junit.jupiter.api.Test;

public class BeanDeserializerTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    public void deserializesNonStaticInnerClassProperty() throws Exception {
        OuterBean outer = MAPPER.readValue("""
                {"inner":{"name":"nested","count":7}}
                """, OuterBean.class);

        assertThat(outer.inner).isNotNull();
        assertThat(outer.inner.name).isEqualTo("nested");
        assertThat(outer.inner.count).isEqualTo(7);
        assertThat(outer.inner.parent()).isSameAs(outer);
    }

    public static class OuterBean {
        public InnerBean inner;

        public class InnerBean {
            public String name;
            public int count;

            public OuterBean parent() {
                return OuterBean.this;
            }
        }
    }
}
