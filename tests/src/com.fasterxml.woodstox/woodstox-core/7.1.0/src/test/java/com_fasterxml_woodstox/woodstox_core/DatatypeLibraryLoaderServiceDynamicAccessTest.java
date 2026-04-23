/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_woodstox.woodstox_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctc.wstx.shaded.msv.relaxng_datatype.Datatype;
import com.ctc.wstx.shaded.msv.relaxng_datatype.DatatypeBuilder;
import com.ctc.wstx.shaded.msv.relaxng_datatype.DatatypeException;
import com.ctc.wstx.shaded.msv.relaxng_datatype.DatatypeLibrary;
import com.ctc.wstx.shaded.msv.relaxng_datatype.DatatypeLibraryFactory;
import com.ctc.wstx.shaded.msv.relaxng_datatype.DatatypeStreamingValidator;
import com.ctc.wstx.shaded.msv.relaxng_datatype.ValidationContext;
import com.ctc.wstx.shaded.msv.relaxng_datatype.helpers.DatatypeLibraryLoader;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

public class DatatypeLibraryLoaderServiceDynamicAccessTest {
    public static final String SERVICE_URI = "urn:test:provider";
    private static final AtomicInteger FACTORY_INSTANTIATIONS = new AtomicInteger();

    @Test
    void instantiatesDatatypeLibraryFactoriesFromServiceEntries() throws Exception {
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        FACTORY_INSTANTIATIONS.set(0);

        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

            DatatypeLibrary library = new DatatypeLibraryLoader().createDatatypeLibrary(SERVICE_URI);

            assertThat(FACTORY_INSTANTIATIONS.get()).isEqualTo(1);
            assertThat(library).isNotNull();
            assertThat(library.createDatatype("token").isValid("native image", null)).isTrue();
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }

    public static final class TestDatatypeLibraryFactory implements DatatypeLibraryFactory {
        public TestDatatypeLibraryFactory() {
            FACTORY_INSTANTIATIONS.incrementAndGet();
        }

        @Override
        public DatatypeLibrary createDatatypeLibrary(String uri) {
            if (!SERVICE_URI.equals(uri)) {
                return null;
            }
            return new TestDatatypeLibrary();
        }
    }

    private static final class TestDatatypeLibrary implements DatatypeLibrary {
        private static final Datatype DATATYPE = new TestDatatype();

        @Override
        public DatatypeBuilder createDatatypeBuilder(String typeLocalName) throws DatatypeException {
            if (!"token".equals(typeLocalName)) {
                throw new DatatypeException(typeLocalName);
            }
            return new DatatypeBuilder() {
                @Override
                public void addParameter(String name, String value, ValidationContext context) {
                }

                @Override
                public Datatype createDatatype() {
                    return DATATYPE;
                }
            };
        }

        @Override
        public Datatype createDatatype(String typeLocalName) throws DatatypeException {
            return createDatatypeBuilder(typeLocalName).createDatatype();
        }
    }

    private static final class TestDatatype implements Datatype {
        @Override
        public boolean isValid(String literal, ValidationContext context) {
            return literal != null && !literal.isBlank();
        }

        @Override
        public void checkValid(String literal, ValidationContext context) throws DatatypeException {
            if (!isValid(literal, context)) {
                throw new DatatypeException("value must not be blank");
            }
        }

        @Override
        public DatatypeStreamingValidator createStreamingValidator(ValidationContext context) {
            return new DatatypeStreamingValidator() {
                private final StringBuilder buffer = new StringBuilder();

                @Override
                public void addCharacters(char[] buf, int start, int len) {
                    buffer.append(buf, start, len);
                }

                @Override
                public boolean isValid() {
                    return TestDatatype.this.isValid(buffer.toString(), context);
                }

                @Override
                public void checkValid() throws DatatypeException {
                    TestDatatype.this.checkValid(buffer.toString(), context);
                }
            };
        }

        @Override
        public Object createValue(String literal, ValidationContext context) {
            return isValid(literal, context) ? literal : null;
        }

        @Override
        public boolean sameValue(Object lhs, Object rhs) {
            return lhs.equals(rhs);
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
            return false;
        }
    }
}
