/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_module.jackson_module_jakarta_xmlbind_annotations;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.Collections;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.module.jakarta.xmlbind.JakartaXmlBindAnnotationIntrospector;
import com.fasterxml.jackson.module.jakarta.xmlbind.JakartaXmlBindAnnotationModule;
import jakarta.activation.DataHandler;
import jakarta.xml.bind.annotation.XmlEnumValue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JakartaXmlBindAnnotationIntrospectorTest {

    @Test
    void createsDefaultDataHandlerConverters() {
        JakartaXmlBindAnnotationIntrospector introspector = new JakartaXmlBindAnnotationIntrospector(
                TypeFactory.defaultInstance());
        Annotated dataHandlerType = new RawTypeAnnotated(DataHandler.class);

        JsonSerializer<?> serializer = introspector.findSerializer(dataHandlerType);
        Object deserializer = introspector.findDeserializer(dataHandlerType);

        assertThat(serializer).isNotNull();
        assertThat(deserializer).isInstanceOf(JsonDeserializer.class);
    }

    @Test
    void resolvesEnumValuesFromXmlEnumValueAnnotations() throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JakartaXmlBindAnnotationModule());

        String activeValue = mapper.writeValueAsString(Status.ACTIVE);
        String unknownValue = mapper.writeValueAsString(Status.UNKNOWN);
        Status inactiveStatus = mapper.readValue("\"inactive-status\"", Status.class);

        assertThat(activeValue).isEqualTo("\"active-status\"");
        assertThat(unknownValue).isEqualTo("\"UNKNOWN\"");
        assertThat(inactiveStatus).isEqualTo(Status.INACTIVE);
    }

    @Test
    @SuppressWarnings("deprecation")
    void resolvesLegacyEnumValuesFromXmlEnumValueAnnotations() {
        JakartaXmlBindAnnotationIntrospector introspector = new JakartaXmlBindAnnotationIntrospector(
                TypeFactory.defaultInstance());
        String[] names = new String[] {"ACTIVE", "INACTIVE", "UNKNOWN"};

        String[] resolvedNames = introspector.findEnumValues(Status.class, Status.values(), names);

        assertThat(resolvedNames).containsExactly("active-status", "inactive-status", "UNKNOWN");
    }

    private static final class RawTypeAnnotated extends Annotated {
        private final Class<?> rawType;
        private final JavaType type;

        private RawTypeAnnotated(Class<?> rawType) {
            this.rawType = rawType;
            this.type = TypeFactory.defaultInstance().constructType(rawType);
        }

        @Override
        public <A extends Annotation> A getAnnotation(Class<A> annotationClass) {
            return null;
        }

        @Override
        public boolean hasAnnotation(Class<?> annotationClass) {
            return false;
        }

        @Override
        public boolean hasOneOf(Class<? extends Annotation>[] annotationClasses) {
            return false;
        }

        @Override
        public AnnotatedElement getAnnotated() {
            return rawType;
        }

        @Override
        protected int getModifiers() {
            return 0;
        }

        @Override
        public String getName() {
            return rawType.getName();
        }

        @Override
        public JavaType getType() {
            return type;
        }

        @Override
        public Class<?> getRawType() {
            return rawType;
        }

        @Override
        public Iterable<Annotation> annotations() {
            return Collections.emptyList();
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof RawTypeAnnotated)) {
                return false;
            }
            RawTypeAnnotated that = (RawTypeAnnotated) other;
            return rawType.equals(that.rawType);
        }

        @Override
        public int hashCode() {
            return rawType.hashCode();
        }

        @Override
        public String toString() {
            return rawType.getName();
        }
    }

    private enum Status {
        @XmlEnumValue("active-status")
        ACTIVE,

        @XmlEnumValue("inactive-status")
        INACTIVE,

        UNKNOWN
    }
}
