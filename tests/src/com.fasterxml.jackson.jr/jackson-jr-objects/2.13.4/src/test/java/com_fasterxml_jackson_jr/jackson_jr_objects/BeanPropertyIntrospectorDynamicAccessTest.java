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
    void deserializesBeansUsingDeclaredFieldsMethodsAndConstructors() throws Exception {
        IntrospectedBean objectBean = JSON_WITH_FORCE_ACCESS.beanFrom(IntrospectedBean.class,
                "{\"visible\":7,\"name\":\"Ada\",\"active\":true}");
        IntrospectedBean stringBean = JSON_WITH_FORCE_ACCESS.beanFrom(IntrospectedBean.class, "\"Grace\"");
        IntrospectedBean longBean = JSON_WITH_FORCE_ACCESS.beanFrom(IntrospectedBean.class, "11");

        assertThat(objectBean.visible).isEqualTo(7);
        assertThat(objectBean.getName()).isEqualTo("Ada");
        assertThat(objectBean.isActive()).isTrue();
        assertThat(stringBean.getName()).isEqualTo("Grace");
        assertThat(longBean.visible).isEqualTo(11);
    }

    @Test
    void serializesPropertiesCollectedFromInheritedFieldsAndDeclaredGetters() throws Exception {
        IntrospectedBean bean = new IntrospectedBean();
        bean.visible = 3;
        bean.setName("Lin");
        bean.setActive(true);

        String json = JSON_WITH_FORCE_ACCESS.asString(bean);

        assertThat(json)
                .contains("\"visible\":3")
                .contains("\"name\":\"Lin\"")
                .contains("\"active\":true");
    }

    static class BaseBean {
        public int visible;
    }

    static final class IntrospectedBean extends BaseBean {
        private String name;
        private boolean active;

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

        private void setName(String name) {
            this.name = name;
        }

        public boolean isActive() {
            return active;
        }

        public void setActive(boolean active) {
            this.active = active;
        }
    }
}
