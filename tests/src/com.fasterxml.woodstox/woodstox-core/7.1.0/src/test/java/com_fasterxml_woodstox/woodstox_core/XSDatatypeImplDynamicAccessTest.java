/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_woodstox.woodstox_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctc.wstx.shaded.msv_core.datatype.xsd.XSDatatypeImpl;
import org.junit.jupiter.api.Test;

public class XSDatatypeImplDynamicAccessTest {
    @Test
    void localizesDatatypeMessagesFromResourceBundles() {
        String message = XSDatatypeImpl.localize(XSDatatypeImpl.ERR_INVALID_BASE_TYPE, "derivedType");

        assertThat(message).contains("derivedType");
    }
}
