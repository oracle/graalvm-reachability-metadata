/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_core.jackson_databind;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BeanDeserializerBaseTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void deserializesNonStaticInnerClassValuedProperty() throws JsonProcessingException {
        OuterBean bean = MAPPER.readValue("""
                {"name":"parent","child":{"value":"nested"}}
                """, OuterBean.class);

        assertThat(bean.name).isEqualTo("parent");
        assertThat(bean.child.value).isEqualTo("nested");
        assertThat(bean.child.outerName()).isEqualTo("parent");
    }

    public static final class OuterBean {
        public String name;
        public InnerBean child;

        public class InnerBean {
            public String value;

            public String outerName() {
                return OuterBean.this.name;
            }
        }
    }
}
