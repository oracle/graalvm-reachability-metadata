/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_xml_bind_external.relaxng_datatype;

import com.sun.tools.rngdatatype.Datatype;
import com.sun.tools.rngdatatype.DatatypeBuilder;
import com.sun.tools.rngdatatype.DatatypeException;
import com.sun.tools.rngdatatype.DatatypeLibrary;
import com.sun.tools.rngdatatype.ValidationContext;
import com.sun.tools.rngdatatype.helpers.ProxyDatatypeLibraryFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProxyDatatypeLibraryFactoryAnonymous1Anonymous1Test {
    private static final String TEST_LIBRARY_URI = "urn:relaxng-datatype-builder-proxy-test";
    private static final String BASE_TYPE = "token";
    private static final String PARAMETER_NAME = "prefix";
    private static final String PARAMETER_VALUE = "xml";
    private static final String BASE_URI = "urn:relaxng-datatype-builder-proxy-base";
    private static final String XML_NAMESPACE_URI = "http://www.w3.org/XML/1998/namespace";
    private static final String VALID_LITERAL = BASE_TYPE + ":" + PARAMETER_NAME + "=" + PARAMETER_VALUE;

    @Test
    void builderAddParameterAndCreateDatatypeAdaptLegacyDatatypeThroughProxies() throws DatatypeException {
        DatatypeLibrary library = new ProxyDatatypeLibraryFactory().createDatatypeLibrary(TEST_LIBRARY_URI);
        assertNotNull(library);

        DatatypeBuilder builder = library.createDatatypeBuilder(BASE_TYPE);
        builder.addParameter(PARAMETER_NAME, PARAMETER_VALUE, new TestValidationContext());
        Datatype datatype = builder.createDatatype();

        assertTrue(datatype.isValid(VALID_LITERAL, new TestValidationContext()));
        assertFalse(datatype.isValid("invalid", new TestValidationContext()));
        assertEquals(Datatype.ID_TYPE_NULL, datatype.getIdType());
        assertTrue(datatype.isContextDependent());
        assertEquals(VALID_LITERAL, datatype.createValue(VALID_LITERAL, new TestValidationContext()));
        assertNull(datatype.createValue("invalid", new TestValidationContext()));
        assertTrue(datatype.sameValue(VALID_LITERAL, VALID_LITERAL));
        assertEquals(VALID_LITERAL.hashCode(), datatype.valueHashCode(VALID_LITERAL));
    }

    public static final class LegacyDatatypeLibraryFactory implements org.relaxng.datatype.DatatypeLibraryFactory {
        public LegacyDatatypeLibraryFactory() {
        }

        @Override
        public org.relaxng.datatype.DatatypeLibrary createDatatypeLibrary(String uri) {
            if (TEST_LIBRARY_URI.equals(uri)) {
                return new LegacyDatatypeLibrary();
            }
            return null;
        }
    }

    private static final class LegacyDatatypeLibrary implements org.relaxng.datatype.DatatypeLibrary {
        @Override
        public org.relaxng.datatype.DatatypeBuilder createDatatypeBuilder(String baseTypeLocalName) {
            return new LegacyDatatypeBuilder(baseTypeLocalName);
        }

        @Override
        public org.relaxng.datatype.Datatype createDatatype(String typeLocalName) {
            return new LegacyDatatype(typeLocalName, null);
        }
    }

    private static final class LegacyDatatypeBuilder implements org.relaxng.datatype.DatatypeBuilder {
        private final String baseTypeLocalName;
        private String parameterValue;

        private LegacyDatatypeBuilder(String baseTypeLocalName) {
            this.baseTypeLocalName = baseTypeLocalName;
        }

        @Override
        public void addParameter(String name, String strValue, org.relaxng.datatype.ValidationContext context)
                throws org.relaxng.datatype.DatatypeException {
            assertEquals(PARAMETER_NAME, name);
            assertEquals(PARAMETER_VALUE, strValue);
            assertNotNull(context);
            parameterValue = strValue;
        }

        @Override
        public org.relaxng.datatype.Datatype createDatatype() {
            return new LegacyDatatype(baseTypeLocalName, parameterValue);
        }
    }

    private static final class LegacyDatatype implements org.relaxng.datatype.Datatype {
        private final String baseTypeLocalName;
        private final String parameterValue;

        private LegacyDatatype(String baseTypeLocalName, String parameterValue) {
            this.baseTypeLocalName = baseTypeLocalName;
            this.parameterValue = parameterValue;
        }

        @Override
        public boolean isValid(String literal, org.relaxng.datatype.ValidationContext context) {
            return literal.equals(baseTypeLocalName + ":" + PARAMETER_NAME + "=" + parameterValue) && context != null;
        }

        @Override
        public void checkValid(String literal, org.relaxng.datatype.ValidationContext context)
                throws org.relaxng.datatype.DatatypeException {
            if (!isValid(literal, context)) {
                throw new org.relaxng.datatype.DatatypeException();
            }
        }

        @Override
        public org.relaxng.datatype.DatatypeStreamingValidator createStreamingValidator(
                org.relaxng.datatype.ValidationContext context) {
            throw new UnsupportedOperationException("Streaming validation is outside this builder proxy test.");
        }

        @Override
        public Object createValue(String literal, org.relaxng.datatype.ValidationContext context) {
            if (isValid(literal, context)) {
                return literal;
            }
            return null;
        }

        @Override
        public boolean sameValue(Object value1, Object value2) {
            return value1.equals(value2);
        }

        @Override
        public int valueHashCode(Object value) {
            return value.hashCode();
        }

        @Override
        public int getIdType() {
            return org.relaxng.datatype.Datatype.ID_TYPE_NULL;
        }

        @Override
        public boolean isContextDependent() {
            return true;
        }
    }

    private static final class TestValidationContext implements ValidationContext {
        @Override
        public String resolveNamespacePrefix(String prefix) {
            if (PARAMETER_VALUE.equals(prefix)) {
                return XML_NAMESPACE_URI;
            }
            return null;
        }

        @Override
        public String getBaseUri() {
            return BASE_URI;
        }

        @Override
        public boolean isUnparsedEntity(String entityName) {
            return false;
        }

        @Override
        public boolean isNotation(String notationName) {
            return false;
        }
    }
}
