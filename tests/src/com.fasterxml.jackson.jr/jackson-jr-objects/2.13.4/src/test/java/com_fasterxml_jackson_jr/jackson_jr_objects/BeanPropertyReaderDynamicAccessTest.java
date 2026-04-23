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

public class BeanPropertyReaderDynamicAccessTest {
    @Test
    void populatesFieldBackedAndSetterBackedProperties() throws Exception {
        ReaderBean bean = JSON.std.beanFrom(ReaderBean.class, "{\"id\":3,\"name\":\"Ada\"}");

        assertThat(bean.id).isEqualTo(3);
        assertThat(bean.getName()).isEqualTo("Ada");
    }

    public static class ReaderBean {
        public int id;
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
