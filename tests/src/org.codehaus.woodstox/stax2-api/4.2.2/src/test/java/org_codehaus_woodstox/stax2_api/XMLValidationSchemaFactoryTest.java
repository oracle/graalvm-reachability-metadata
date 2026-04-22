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

    private final String originalImplementationProperty = System.getProperty(IMPLEMENTATION_PROPERTY);

    @AfterEach
    void restoreImplementationProperty() {
        if (originalImplementationProperty == null) {
            System.clearProperty(IMPLEMENTATION_PROPERTY);
        } else {
            System.setProperty(IMPLEMENTATION_PROPERTY, originalImplementationProperty);
        }
    }

    @Test
    void loadsFactoryFromContextClassLoaderServices() {
        System.clearProperty(IMPLEMENTATION_PROPERTY);

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

        XMLValidationSchemaFactory factory = XMLValidationSchemaFactory.newInstance(
                XMLValidationSchema.SCHEMA_ID_W3C_SCHEMA,
                null);

        assertThat(factory).isInstanceOf(ServiceBackedValidationSchemaFactory.class);
        assertThat(factory.getSchemaType()).isEqualTo(XMLValidationSchema.SCHEMA_ID_W3C_SCHEMA);
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
