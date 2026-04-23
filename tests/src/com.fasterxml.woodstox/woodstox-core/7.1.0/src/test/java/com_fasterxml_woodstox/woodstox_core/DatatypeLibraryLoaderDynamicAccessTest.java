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
import com.ctc.wstx.shaded.msv.relaxng_datatype.helpers.DatatypeLibraryLoader;
import org.junit.jupiter.api.Test;

public class DatatypeLibraryLoaderDynamicAccessTest {
    @Test
    void loadsDatatypeLibrariesFromServiceResources() throws Exception {
        DatatypeLibrary library = new DatatypeLibraryLoader()
                .createDatatypeLibrary("http://www.w3.org/2001/XMLSchema-datatypes");

        assertThat(library).isNotNull();

        Datatype datatype = library.createDatatype("string");
        assertThat(datatype).isNotNull();
        assertThat(datatype.isValid("woodstox", null)).isTrue();
    }
}
