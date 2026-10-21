/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kylin.kylin_jdbc;

import java.util.Set;

import org.apache.kylin.jdbc.shaded.com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.apache.kylin.jdbc.shaded.com.fasterxml.jackson.annotation.JsonValue;
import org.apache.kylin.jdbc.shaded.com.fasterxml.jackson.databind.JavaType;
import org.apache.kylin.jdbc.shaded.com.fasterxml.jackson.databind.JsonMappingException;
import org.apache.kylin.jdbc.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kylin.jdbc.shaded.com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper;
import org.apache.kylin.jdbc.shaded.com.fasterxml.jackson.databind.jsonFormatVisitors.JsonStringFormatVisitor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JsonValueSerializerTest {
    @Test
    void serializesAnObjectThroughItsJsonValueMethod() throws Exception {
        assertThat(new ObjectMapper().writeValueAsString(new Identifier("K-19"))).isEqualTo("\"K-19\"");
    }

    @Test
    void serializesAJsonValueObjectWithTypeInformation() throws Exception {
        String json = new ObjectMapper().writeValueAsString(new TypedIdentifier("typed"));

        assertThat(json).contains("TypedIdentifier", "typed");
    }

    @Test
    void reportsJsonValuesForEveryEnumConstant() throws Exception {
        EnumValueVisitor visitor = new EnumValueVisitor();

        new ObjectMapper().acceptJsonFormatVisitor(Status.class, visitor);

        assertThat(visitor.values).containsExactly("ready-value", "done-value");
    }

    public static class EnumValueVisitor extends JsonFormatVisitorWrapper.Base {
        private Set<String> values;

        @Override
        public JsonStringFormatVisitor expectStringFormat(JavaType type) throws JsonMappingException {
            return new JsonStringFormatVisitor.Base() {
                @Override
                public void enumTypes(Set<String> enums) {
                    values = enums;
                }
            };
        }
    }

    public enum Status {
        READY,
        DONE;

        @JsonValue
        public String value() {
            return name().toLowerCase() + "-value";
        }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.WRAPPER_ARRAY)
    public static class TypedIdentifier {
        private final String value;

        public TypedIdentifier(String value) {
            this.value = value;
        }

        @JsonValue
        public String value() {
            return value;
        }
    }

    public static class Identifier {
        private final String value;

        public Identifier(String value) {
            this.value = value;
        }

        @JsonValue
        public String value() {
            return value;
        }
    }
}
