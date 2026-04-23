/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_woodstox.woodstox_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctc.wstx.shaded.msv.relaxng_datatype.Datatype;
import com.ctc.wstx.shaded.msv.relaxng_datatype.DatatypeLibrary;
import com.ctc.wstx.shaded.msv.relaxng_datatype.DatatypeLibraryFactory;
import com.ctc.wstx.shaded.msv.relaxng_datatype.helpers.DatatypeLibraryLoader;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import org.junit.jupiter.api.Test;

public class DatatypeLibraryLoaderDynamicAccessTest {
    private static final VarHandle DATATYPE_LIBRARY_FACTORY_CLASS_CACHE = datatypeLibraryFactoryClassCache();

    @Test
    void loadsDatatypeLibrariesFromServiceResources() throws Throwable {
        Class<?> previousCachedFactoryClass = (Class<?>) DATATYPE_LIBRARY_FACTORY_CLASS_CACHE.get();

        try {
            DATATYPE_LIBRARY_FACTORY_CLASS_CACHE.set(null);

            DatatypeLibrary library = new DatatypeLibraryLoader()
                    .createDatatypeLibrary("http://www.w3.org/2001/XMLSchema-datatypes");

            assertThat(DATATYPE_LIBRARY_FACTORY_CLASS_CACHE.get()).isEqualTo(DatatypeLibraryFactory.class);
            assertThat(library).isNotNull();

            Datatype datatype = library.createDatatype("string");
            assertThat(datatype).isNotNull();
            assertThat(datatype.isValid("woodstox", null)).isTrue();
        } finally {
            DATATYPE_LIBRARY_FACTORY_CLASS_CACHE.set(previousCachedFactoryClass);
        }
    }

    private static VarHandle datatypeLibraryFactoryClassCache() {
        try {
            return MethodHandles.privateLookupIn(DatatypeLibraryLoader.class, MethodHandles.lookup())
                    .findStaticVarHandle(
                            DatatypeLibraryLoader.class,
                            "class$org$relaxng$datatype$DatatypeLibraryFactory",
                            Class.class
                    );
        } catch (ReflectiveOperationException reflectiveOperationException) {
            throw new IllegalStateException(reflectiveOperationException);
        }
    }
}
