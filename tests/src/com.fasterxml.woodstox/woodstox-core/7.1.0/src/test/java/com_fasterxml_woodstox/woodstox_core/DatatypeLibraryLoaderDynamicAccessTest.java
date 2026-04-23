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
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import org.junit.jupiter.api.Test;

public class DatatypeLibraryLoaderDynamicAccessTest {
    @Test
    void loadsDatatypeLibrariesFromServiceResources() throws Exception {
        final DatatypeLibrary library = new DatatypeLibraryLoader()
                .createDatatypeLibrary("http://www.w3.org/2001/XMLSchema-datatypes");

        assertThat(library).isNotNull();

        final Datatype datatype = library.createDatatype("string");
        assertThat(datatype).isNotNull();
        assertThat(datatype.isValid("woodstox", null)).isTrue();
    }

    @Test
    void invokesLegacySyntheticClassLookupHelper() throws Throwable {
        assertThat(invokeSyntheticClassLookup(datatypeLibraryFactoryClassName()))
                .isEqualTo(DatatypeLibraryFactory.class);
    }

    private static String datatypeLibraryFactoryClassName() {
        final char[] className = "com/ctc/wstx/shaded/msv/relaxng_datatype/DatatypeLibraryFactory".toCharArray();
        for (int index = 0; index < className.length; index++) {
            if (className[index] == '/') {
                className[index] = '.';
            }
        }
        return new String(className);
    }

    private static Class<?> invokeSyntheticClassLookup(final String className) throws Throwable {
        final MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                DatatypeLibraryLoader.class,
                MethodHandles.lookup()
        );
        final MethodHandle classLookup = lookup.findStatic(
                DatatypeLibraryLoader.class,
                "class$",
                MethodType.methodType(Class.class, String.class)
        );
        return (Class<?>) classLookup.invokeExact(className);
    }
}
