/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_woodstox.woodstox_core;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ctc.wstx.shaded.msv_core.grammar.relaxng.datatype.BuiltinDatatypeLibrary;
import org.junit.jupiter.api.Test;

public class DatatypeBuilderImplDynamicAccessTest {
    @Test
    void reportsUnsupportedParametersFromTheBuiltInDatatypeBuilder() throws Exception {
        assertThatThrownBy(() -> BuiltinDatatypeLibrary.theInstance
                .createDatatypeBuilder("string")
                .addParameter("pattern", "[a-z]+", null))
                .hasMessageContaining("no parameter is available for RELAX NG built-in types");
    }
}
