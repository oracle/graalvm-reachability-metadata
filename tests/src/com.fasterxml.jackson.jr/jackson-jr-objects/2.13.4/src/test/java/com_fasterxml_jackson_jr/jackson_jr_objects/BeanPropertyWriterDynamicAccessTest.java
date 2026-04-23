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

public class BeanPropertyWriterDynamicAccessTest {
    @Test
    void serializesFieldBackedAndGetterBackedProperties() throws Exception {
        WriterBean bean = new WriterBean();
        String json = JSON.std.asString(bean);

        assertThat(json).contains("\"id\":3").contains("\"name\":\"Ada\"");
    }

    public static class WriterBean {
        public int id = 3;
        private String name = "Ada";

        public String getName() {
            return name;
        }
    }
}
