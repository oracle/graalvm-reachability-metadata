/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_jr.jackson_jr_objects;

import com.fasterxml.jackson.jr.ob.JSON;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BeanPropertyIntrospectorDynamicAccessTest {
    @Test
    void introspectsConstructorsFieldsAndMethodsDuringBeanBinding() throws Exception {
        IntrospectedBean bean = JSON.std.beanFrom(IntrospectedBean.class, "{\"visible\":7,\"name\":\"Ada\"}");

        assertThat(bean.visible).isEqualTo(7);
        assertThat(bean.getName()).isEqualTo("Ada");
        assertThat(JSON.std.asString(bean)).contains("\"visible\":7").contains("\"name\":\"Ada\"");
    }

    public static class IntrospectedBean {
        public int visible;
        private String name;

        public IntrospectedBean() {
        }

        public IntrospectedBean(String name) {
            this.name = name;
        }

        public IntrospectedBean(long visible) {
            this.visible = (int) visible;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
