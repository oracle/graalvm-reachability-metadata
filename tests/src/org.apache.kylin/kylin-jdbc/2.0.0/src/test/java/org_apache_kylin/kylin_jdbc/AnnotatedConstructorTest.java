/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kylin.kylin_jdbc;

import org.apache.kylin.jdbc.shaded.com.fasterxml.jackson.databind.BeanDescription;
import org.apache.kylin.jdbc.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kylin.jdbc.shaded.com.fasterxml.jackson.databind.introspect.AnnotatedConstructor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AnnotatedConstructorTest {
    @Test
    void invokesConstructorsDiscoveredByJacksonIntrospection() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        BeanDescription description = mapper.getDeserializationConfig().introspect(mapper.constructType(Entry.class));
        AnnotatedConstructor oneArgument = description.getConstructors().get(0);

        assertThat(description.findDefaultConstructor().call()).isEqualTo(new Entry("default"));
        assertThat(oneArgument.call1("selected")).isEqualTo(new Entry("selected"));
    }

    public static class Entry {
        private final String value;

        public Entry() {
            this("default");
        }

        public Entry(String value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Entry && value.equals(((Entry) other).value);
        }
    }
}
