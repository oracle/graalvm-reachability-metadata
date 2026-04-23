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
    private static final JSON JSON_WITH_FORCE_ACCESS = JSON.std.with(JSON.Feature.FORCE_REFLECTION_ACCESS);

    @Test
    void serializesFieldBackedProperties() throws Exception {
        String json = JSON_WITH_FORCE_ACCESS.asString(new FieldBackedWriterBean());

        assertThat(json).contains("\"id\":3");
    }

    @Test
    void serializesGetterBackedProperties() throws Exception {
        String json = JSON_WITH_FORCE_ACCESS.asString(new GetterBackedWriterBean());

        assertThat(json).contains("\"name\":\"Ada\"");
    }

    static final class FieldBackedWriterBean {
        public int id = 3;
    }

    static final class GetterBackedWriterBean {
        private final String name = "Ada";

        public String getName() {
            return name;
        }
    }
}
