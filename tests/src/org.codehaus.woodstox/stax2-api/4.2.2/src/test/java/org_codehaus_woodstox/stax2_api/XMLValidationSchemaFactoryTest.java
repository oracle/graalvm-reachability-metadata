/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_woodstox.stax2_api;

import java.io.File;
import java.io.InputStream;
import java.io.Reader;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.codehaus.stax2.validation.ValidationContext;
import org.codehaus.stax2.validation.XMLValidationSchema;
import org.codehaus.stax2.validation.XMLValidationSchemaFactory;
import org.codehaus.stax2.validation.XMLValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class XMLValidationSchemaFactoryTest {
    private static final String IMPLEMENTATION_PROPERTY =
            XMLValidationSchemaFactory.SYSTEM_PROPERTY_FOR_IMPL + XMLValidationSchemaFactory.INTERNAL_ID_SCHEMA_W3C;
    private static final String JAVA_HOME_PROPERTY = "java.home";

    private final String originalImplementationProperty = System.getProperty(IMPLEMENTATION_PROPERTY);
    private final String originalJavaHomeProperty = System.getProperty(JAVA_HOME_PROPERTY);

    @AfterEach
    void restoreImplementationProperty() {
        if (originalImplementationProperty == null) {
            System.clearProperty(IMPLEMENTATION_PROPERTY);
        } else {
            System.setProperty(IMPLEMENTATION_PROPERTY, originalImplementationProperty);
        }

        if (originalJavaHomeProperty == null) {
            System.clearProperty(JAVA_HOME_PROPERTY);
        } else {
            System.setProperty(JAVA_HOME_PROPERTY, originalJavaHomeProperty);
        }
    }

    @Test
    void loadsFactoryFromContextClassLoaderServices() {
        System.clearProperty(IMPLEMENTATION_PROPERTY);
        ensureJavaHomeProperty();

        ClassLoader classLoader = XMLValidationSchemaFactoryTest.class.getClassLoader();
        XMLValidationSchemaFactory factory = XMLValidationSchemaFactory.newInstance(
                XMLValidationSchema.SCHEMA_ID_W3C_SCHEMA,
                classLoader);

        assertThat(factory).isInstanceOf(ServiceBackedValidationSchemaFactory.class);
        assertThat(factory.getSchemaType()).isEqualTo(XMLValidationSchema.SCHEMA_ID_W3C_SCHEMA);
    }

    @Test
    void loadsFactoryFromSystemResourcesWhenClassLoaderIsNull() {
        System.clearProperty(IMPLEMENTATION_PROPERTY);
        ensureJavaHomeProperty();

        XMLValidationSchemaFactory factory = XMLValidationSchemaFactory.newInstance(
                XMLValidationSchema.SCHEMA_ID_W3C_SCHEMA,
                null);

        assertThat(factory).isInstanceOf(ServiceBackedValidationSchemaFactory.class);
        assertThat(factory.getSchemaType()).isEqualTo(XMLValidationSchema.SCHEMA_ID_W3C_SCHEMA);
    }

    private static void ensureJavaHomeProperty() {
        if (System.getProperty(JAVA_HOME_PROPERTY) == null) {
            System.setProperty(JAVA_HOME_PROPERTY, "java-home");
        }
    }

    public static final class ServiceBackedValidationSchemaFactory extends XMLValidationSchemaFactory {
        private static final StubValidationSchema SCHEMA = new StubValidationSchema();

        private final Map<String, Object> properties = new HashMap<>();

        public ServiceBackedValidationSchemaFactory() {
            super(XMLValidationSchema.SCHEMA_ID_W3C_SCHEMA);
        }

        @Override
        public XMLValidationSchema createSchema(InputStream in, String encoding, String publicId, String systemId) {
            return SCHEMA;
        }

        @Override
        public XMLValidationSchema createSchema(Reader r, String publicId, String systemId) {
            return SCHEMA;
        }

        @Override
        public XMLValidationSchema createSchema(URL url) {
            return SCHEMA;
        }

        @Override
        public XMLValidationSchema createSchema(File f) {
            return SCHEMA;
        }

        @Override
        public boolean isPropertySupported(String propName) {
            return properties.containsKey(propName);
        }

        @Override
        public boolean setProperty(String propName, Object value) {
            properties.put(propName, value);
            return true;
        }

        @Override
        public Object getProperty(String propName) {
            return properties.get(propName);
        }
    }

    private static final class StubValidationSchema implements XMLValidationSchema {
        @Override
        public XMLValidator createValidator(ValidationContext ctxt) {
            return null;
        }

        @Override
        public String getSchemaType() {
            return XMLValidationSchema.SCHEMA_ID_W3C_SCHEMA;
        }
    }
}
