/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_woodstox.stax2_api;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.stream.XMLStreamException;

import org.codehaus.stax2.validation.ValidationContext;
import org.codehaus.stax2.validation.XMLValidationSchema;
import org.codehaus.stax2.validation.XMLValidationSchemaFactory;
import org.codehaus.stax2.validation.XMLValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class XMLValidationSchemaFactoryTest {

    private static final String FACTORY_PROPERTY = XMLValidationSchemaFactory.SYSTEM_PROPERTY_FOR_IMPL
            + XMLValidationSchemaFactory.INTERNAL_ID_SCHEMA_DTD;

    private String originalFactoryProperty;
    private String originalJavaHome;
    private Path temporaryJavaHome;

    @BeforeEach
    void prepareServiceLookup() throws IOException {
        originalFactoryProperty = System.getProperty(FACTORY_PROPERTY);
        System.clearProperty(FACTORY_PROPERTY);

        originalJavaHome = System.getProperty("java.home");
        temporaryJavaHome = Files.createTempDirectory("stax2-java-home");
        System.setProperty("java.home", temporaryJavaHome.toString());
    }

    @AfterEach
    void restoreJvmProperties() throws IOException {
        if (originalFactoryProperty == null) {
            System.clearProperty(FACTORY_PROPERTY);
        } else {
            System.setProperty(FACTORY_PROPERTY, originalFactoryProperty);
        }

        if (originalJavaHome == null) {
            System.clearProperty("java.home");
        } else {
            System.setProperty("java.home", originalJavaHome);
        }

        if (temporaryJavaHome != null) {
            Files.deleteIfExists(temporaryJavaHome);
        }
    }

    @Test
    void loadsFactoryFromSystemResourcesWhenNoClassLoaderIsProvided() {
        XMLValidationSchemaFactory factory = XMLValidationSchemaFactory.newInstance(XMLValidationSchema.SCHEMA_ID_DTD, null);

        assertThat(factory).isInstanceOf(StubValidationSchemaFactory.class);
        assertThat(factory.getSchemaType()).isEqualTo(XMLValidationSchema.SCHEMA_ID_DTD);
    }

    @Test
    void loadsFactoryFromExplicitClassLoaderResources() {
        ClassLoader classLoader = XMLValidationSchemaFactoryTest.class.getClassLoader();

        XMLValidationSchemaFactory factory = XMLValidationSchemaFactory.newInstance(XMLValidationSchema.SCHEMA_ID_DTD, classLoader);

        assertThat(factory).isInstanceOf(StubValidationSchemaFactory.class);
        assertThat(factory.getSchemaType()).isEqualTo(XMLValidationSchema.SCHEMA_ID_DTD);
    }

    public static class StubValidationSchemaFactory extends XMLValidationSchemaFactory {

        private static final XMLValidationSchema SCHEMA = new StubValidationSchema();

        public StubValidationSchemaFactory() {
            super(XMLValidationSchema.SCHEMA_ID_DTD);
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
            return false;
        }

        @Override
        public boolean setProperty(String propName, Object value) {
            return false;
        }

        @Override
        public Object getProperty(String propName) {
            return null;
        }
    }

    private static final class StubValidationSchema implements XMLValidationSchema {

        @Override
        public XMLValidator createValidator(ValidationContext ctxt) throws XMLStreamException {
            return null;
        }

        @Override
        public String getSchemaType() {
            return XMLValidationSchema.SCHEMA_ID_DTD;
        }
    }
}
