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
    private static final JSON JSON_WITH_FORCE_ACCESS = JSON.std.with(JSON.Feature.FORCE_REFLECTION_ACCESS);

    @Test
    void introspectsDeclaredConstructorsFieldsAndMethodsDuringBeanRoundTrip() throws Exception {
        IntrospectedBean bean = JSON_WITH_FORCE_ACCESS.beanFrom(IntrospectedBean.class,
                "{\"visible\":7,\"name\":\"Ada\"}");

        assertThat(bean.visible()).isEqualTo(7);
        assertThat(bean.getName()).isEqualTo("Ada");
        assertThat(JSON_WITH_FORCE_ACCESS.asString(bean))
                .contains("\"visible\":7")
                .contains("\"name\":\"Ada\"");
    }

    static final class IntrospectedBean {
        private int visible;
        private String name;

        private IntrospectedBean() {
        }

        private IntrospectedBean(String name) {
            this.name = name;
        }

        private IntrospectedBean(long visible) {
            this.visible = (int) visible;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getVisible() {
            return visible;
        }

        public void setVisible(int visible) {
            this.visible = visible;
        }

        int visible() {
            return visible;
        }
    }
}
