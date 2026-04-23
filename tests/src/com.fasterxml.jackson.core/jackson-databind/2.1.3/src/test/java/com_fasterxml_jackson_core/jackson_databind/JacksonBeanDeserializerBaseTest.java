/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_core.jackson_databind;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JacksonBeanDeserializerBaseTest {

    @Test
    void beanDeserializerBaseResolvesInnerClassConstructors() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        OuterBean bean = mapper.readValue("{\"child\":{\"value\":\"resolved\"}}", OuterBean.class);
        assertThat(bean.child.value).isEqualTo("resolved");
    }

    static class OuterBean {

        public Child child;

        public class Child {

            public String value;
        }
    }
}
