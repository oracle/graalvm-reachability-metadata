/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_xml_bind_external.relaxng_datatype;

import com.sun.tools.rngdatatype.Datatype;
import com.sun.tools.rngdatatype.DatatypeException;
import com.sun.tools.rngdatatype.DatatypeLibrary;
import com.sun.tools.rngdatatype.DatatypeStreamingValidator;
import com.sun.tools.rngdatatype.ValidationContext;
import com.sun.tools.rngdatatype.helpers.DatatypeLibraryLoader;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProxyDatatypeLibraryFactoryInnerGIHTest {
    private static final String TEST_LIBRARY_URI = "urn:relaxng-datatype-proxy-test";
    private static final String VALID_LITERAL = "valid-token";

    @Test
    void adaptsLegacyDatatypeThroughProxyInvocationHandler() throws DatatypeException {
        DatatypeLibrary library = new DatatypeLibraryLoader().createDatatypeLibrary(TEST_LIBRARY_URI);
        assertNotNull(library);

        Datatype datatype = library.createDatatype("token");
        ValidationContext context = new TestValidationContext();

        assertTrue(datatype.isValid(VALID_LITERAL, context));
        assertFalse(datatype.isValid("invalid-token", context));
        assertEquals(Datatype.ID_TYPE_NULL, datatype.getIdType());
        assertFalse(datatype.isContextDependent());
        assertSame(VALID_LITERAL, datatype.createValue(VALID_LITERAL, context));
        assertTrue(datatype.sameValue(VALID_LITERAL, VALID_LITERAL));
        assertEquals(VALID_LITERAL.hashCode(), datatype.valueHashCode(VALID_LITERAL));
    }

    @Test
    void adaptsLegacyValidationContextAndStreamingValidatorThroughInvocationHandler() throws Exception {
        org.relaxng.datatype.Datatype legacyDatatype = createLegacyDatatypeProxy(new ModernDatatype());
        org.relaxng.datatype.ValidationContext context = new LegacyValidationContext();

        assertTrue(legacyDatatype.isValid(VALID_LITERAL, context));
        assertThrows(ClassCastException.class, () -> legacyDatatype.createStreamingValidator(context));
    }

    private static org.relaxng.datatype.Datatype createLegacyDatatypeProxy(Datatype datatype) throws Exception {
        Class<?> handlerClass = Class.forName("com.sun.tools.rngdatatype.helpers.ProxyDatatypeLibraryFactory$GIH");
        Constructor<?> constructor = handlerClass.getDeclaredConstructor(Class.class, Object.class);
        constructor.setAccessible(true);
        InvocationHandler handler = (InvocationHandler) constructor.newInstance(Datatype.class, datatype);
        return (org.relaxng.datatype.Datatype) Proxy.newProxyInstance(
                org.relaxng.datatype.Datatype.class.getClassLoader(),
                new Class<?>[]{org.relaxng.datatype.Datatype.class},
                handler);
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
            return new LegacyDatatypeBuilder();
        }

        @Override
        public org.relaxng.datatype.Datatype createDatatype(String typeLocalName) {
            return new LegacyDatatype();
        }
    }

    private static final class LegacyDatatypeBuilder implements org.relaxng.datatype.DatatypeBuilder {
        private final LegacyDatatype datatype = new LegacyDatatype();

        @Override
        public void addParameter(String name, String strValue, org.relaxng.datatype.ValidationContext context) {
        }

        @Override
        public org.relaxng.datatype.Datatype createDatatype() {
            return datatype;
        }
    }

    private static final class LegacyDatatype implements org.relaxng.datatype.Datatype {
        @Override
        public boolean isValid(String literal, org.relaxng.datatype.ValidationContext context) {
            return VALID_LITERAL.equals(literal);
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
            return new LegacyDatatypeStreamingValidator();
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
            return false;
        }
    }

    private static final class LegacyDatatypeStreamingValidator
            implements org.relaxng.datatype.DatatypeStreamingValidator {
        private final StringBuilder literal = new StringBuilder();

        @Override
        public void addCharacters(char[] buf, int start, int len) {
            literal.append(buf, start, len);
        }

        @Override
        public boolean isValid() {
            return VALID_LITERAL.contentEquals(literal);
        }

        @Override
        public void checkValid() throws org.relaxng.datatype.DatatypeException {
            if (!isValid()) {
                throw new org.relaxng.datatype.DatatypeException();
            }
        }
    }

    private static final class ModernDatatype implements Datatype {
        @Override
        public boolean isValid(String literal, ValidationContext context) {
            return VALID_LITERAL.equals(literal) && context != null;
        }

        @Override
        public void checkValid(String literal, ValidationContext context) throws DatatypeException {
            if (!isValid(literal, context)) {
                throw new DatatypeException();
            }
        }

        @Override
        public DatatypeStreamingValidator createStreamingValidator(ValidationContext context) {
            assertNotNull(context);
            return new ModernDatatypeStreamingValidator();
        }

        @Override
        public Object createValue(String literal, ValidationContext context) {
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
            return ID_TYPE_NULL;
        }

        @Override
        public boolean isContextDependent() {
            return true;
        }
    }

    private static final class ModernDatatypeStreamingValidator implements DatatypeStreamingValidator {
        private final StringBuilder literal = new StringBuilder();

        @Override
        public void addCharacters(char[] buf, int start, int len) {
            literal.append(buf, start, len);
        }

        @Override
        public boolean isValid() {
            return VALID_LITERAL.contentEquals(literal);
        }

        @Override
        public void checkValid() throws DatatypeException {
            if (!isValid()) {
                throw new DatatypeException();
            }
        }
    }

    private static final class TestValidationContext implements ValidationContext {
        @Override
        public String resolveNamespacePrefix(String prefix) {
            if ("xml".equals(prefix)) {
                return "http://www.w3.org/XML/1998/namespace";
            }
            return null;
        }

        @Override
        public String getBaseUri() {
            return "urn:relaxng-datatype-proxy-test-base";
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

    private static final class LegacyValidationContext implements org.relaxng.datatype.ValidationContext {
        @Override
        public String resolveNamespacePrefix(String prefix) {
            if ("legacy".equals(prefix)) {
                return "urn:legacy";
            }
            return null;
        }

        @Override
        public String getBaseUri() {
            return "urn:legacy-base";
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
